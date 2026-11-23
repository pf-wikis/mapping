/**
 * Self-contained replacement for Planetiler's tile-rendering and archive stage.
 *
 * <p>The package is internal to the layer compiler: it consumes the prepared
 * {@code FeatureCollection} layers, emits MapLibre Tiles (MLT) and writes them to
 * a v3 PMTiles archive. It deliberately mirrors Planetiler's per-zoom scaling,
 * simplification, tiling, polygon snapping, geometry quantization and feature
 * ordering so that the decoded tile content is identical to the previous
 * Planetiler-based pipeline for the same input layers.
 *
 * <h2>Pipeline</h2>
 *
 * <pre>
 * MltPmtilesCompiler
 *   |
 *   +-- FeatureTiler ---------- per-zoom scale/simplify/tile
 *   |     |-- TiledGeometry ------------- clip geometry into tiles
 *   |     |-- GeometryCoordinateSequences reassemble tile-local geometry
 *   |     |-- GeoUtils.snapAndFixPolygon  snap to tile precision
 *   |     |-- TileGeometryCodec ---------- Planetiler-compatible quantization
 *   |     |-- FillRuns ------------------- filled area -> Hilbert runs
 *   |     +-- TileStore ------------------ bounded-memory spill of sorted runs
 *   |
 *   +-- TileMerger ------------- merge boundary/fill runs by tile id
 *   +-- MltTileEncoder --------- MapLibre Tiles encoding (MLT 0.0.12)
 *   +-- PmtilesWriter ---------- streaming PMTiles v3 archive
 * </pre>
 *
 * <h2>Why the pipeline is streaming</h2>
 *
 * <p>At zoom 15 the world map addresses roughly 900 million tiles. Keeping one
 * object per tile feature in memory needs tens of gigabytes and previously made
 * a {@code maxZoom=15} production build fail with {@link java.lang.OutOfMemoryError}.
 * The implementation therefore never materializes the final tile set:
 *
 * <ul>
 *   <li>{@link io.github.pfwikis.tile.TileStore} accumulates tile features in a
 *       bounded in-memory map and spills sorted runs to temporary files once a
 *       configurable threshold is reached
 *       ({@code -Dtile.spillThreshold}, default {@code 1000000}).</li>
 *   <li>{@link io.github.pfwikis.tile.FillRuns} converts fully covered areas
 *       into contiguous Hilbert-id runs instead of one record per tile. Because
 *       the Hilbert curve visits every aligned {@code 2^k x 2^k} square as one
 *       contiguous index block, an aligned block decomposition of a filled area
 *       produces a few thousand runs instead of hundreds of millions of tiles.</li>
 *   <li>{@link io.github.pfwikis.tile.TileMerger} sweeps boundary runs and fill
 *       runs together and yields only those id ranges whose tile content is
 *       constant. Encoding therefore happens once per content run, not once per
 *       addressed tile.</li>
 *   <li>{@link io.github.pfwikis.tile.PmtilesWriter} spools the directory
 *       entries to a temporary file, deduplicates tile contents with a primitive
 *       hash map and writes the archive sequentially.</li>
 * </ul>
 *
 * <p>The resulting peak memory is independent of the number of output tiles and
 * fits in the default JVM heap; a {@code maxZoom=15} build uses roughly 9 GB of
 * resident memory including the upstream layer pipeline.
 *
 * <h2>Why this implementation is faster than Planetiler</h2>
 *
 * <p>Both implementations emit the same decoded tile content, but their cost
 * models are very different. Planetiler is a general-purpose pipeline: it
 * materializes one rendered feature per tile, externally sorts all rendered
 * features by tile id, and then encodes tiles through its generic archive
 * writer. At zoom 15 that means writing, sorting and reading back roughly two
 * billion feature records (about 83 GB of temporary data in a local
 * {@code maxZoom=15} run) before the first tile is encoded.
 *
 * <p>This package is specialized for the prepared map layers, MLT and PMTiles,
 * and exploits two properties of that data:
 *
 * <ol>
 *   <li><b>Filled tiles repeat.</b> Planetiler emits one feature for every
 *       fully covered tile; zoom 15 alone addresses about 895 million tiles,
 *       most of them filled polygons with only a handful of distinct contents.
 *       {@link io.github.pfwikis.tile.FillRuns} partitions the same filled area
 *       into aligned {@code 64 x 64} Hilbert blocks: fully filled blocks become
 *       one run, and only blocks touching a polygon boundary are expanded tile
 *       by tile. {@link io.github.pfwikis.tile.TileMerger} sweeps those runs together
 *       with boundary runs. Encoding and directory construction therefore work
 *       on content-constant runs instead of every addressed tile.</li>
 *   <li><b>Tile schemas repeat.</b> A high-zoom tile is usually a single fill or
 *       a small fixed combination of fills. Its MLT bytes depend only on the
 *       layer and attribute set, so {@link io.github.pfwikis.tile.MltPmtilesCompiler}
 *       encodes each combination once and reuses the compressed result.</li>
 * </ol>
 *
 * <p>Approximate cost for a production-style {@code maxZoom=15 -prodDetail}
 * build of the current map on the same machine and inputs, with the default
 * JVM heap:
 *
 * <pre>
 *                                  Planetiler          this package
 * per-filled-tile records          ~1.96 billion       none (Hilbert runs)
 * records externally sorted        ~2 billion          ~15 million boundary
 * encode/write iterations          ~895 million tiles  ~17.5 million runs
 * temporary disk                   ~83 GB              2-4 GB spill files
 * COMPILE_TILES time               310 s               82 s
 * total wall time                  353 s               125 s
 * </pre>
 *
 * <p>On the same z15 inputs, pre-tessellation changes the tile phase from about
 * 75.8 s to 76.5 s and the archive from 1.81 GB to 2.11 GB, because the triangle
 * mesh is stored alongside the ring topology.
 *
 * <p>The addressed tile count is identical ({@code 895082460} in the measured
 * run), so the speedup is not caused by emitting fewer tiles; it comes from
 * avoiding the generic feature sort, encoding repeated content once, and
 * writing the archive directly.
 *
 * <p>Concretely:
 *
 * <ul>
 *   <li><b>No global feature sort.</b> Only boundary features are spilled to
 *       sorted runs; filled areas are represented as interval runs. Planetiler
 *       has to external-sort every rendered feature, including all fills, then
 *       merge the sorted chunks.</li>
 *   <li><b>No per-tile Planetiler objects.</b> There is no
 *       {@code RenderedFeature}, {@code VectorTile.Feature}, feature group,
 *       sort key or id bookkeeping for each of the hundreds of millions of
 *       addressed tiles. Tile features stay compact command arrays until a
 *       content run is encoded.</li>
 *   <li><b>Direct MLT path.</b> Geometry commands are decoded to JTS once and
 *       passed straight to the MLT converter. Planetiler builds an intermediate
 *       vector-tile representation and performs its own archive/feature-table
 *       management around the same converter.</li>
 *   <li><b>Direct PMTiles writer.</b> Tile data is streamed sequentially with a
 *       large buffer, directory entries are spooled to a temporary file,
 *       deduplication uses a primitive {@code long -> long} hash map, and
 *       identical fill runs are collapsed into one directory entry with a run
 *       length. Planetiler's generic archive writer retains boxed entry objects
 *       for every tile entry and performs more format-independent work.</li>
 *   <li><b>Compression is independent of tiling.</b> Tile payloads are compressed
 *       as a whole with gzip at the best level. This changes only the stored
 *       bytes, not the decoded MLT content or the deduplication of identical
 *       tiles.</li>
 *   <li><b>Tiling strategy.</b> Layers are processed one at a time so prepared
 *       source geometry can be released early; features, large polygon tiles and
 *       fill decomposition are parallelized; big features no longer become a
 *       single serial tail task.</li>
 * </ul>
 *
 * <p>The trade-off is specialization: the package assumes the compiler's layer
 * model, MLT output and PMTiles output. It is not a general Planetiler
 * replacement, but for this pipeline it removes the dominant general-purpose
 * costs without changing the decoded result.
 *
 * <h2>Pre-tessellation and compression</h2>
 *
 * <p>MLT polygon pre-tessellation is enabled by default (disable with
 * {@code -Dtile.preTessellate=false}). The original outline topology is kept for
 * every feature table ({@code outlineFeatureTableNames("ALL")}) so clients can
 * reconstruct polygon rings. MapLibre GL JS currently reconstructs rings and
 * tessellates them again rather than drawing the mesh, so the web frontend pays
 * the larger archive without yet getting the mesh rendering benefit.
 *
 * <p>PMTiles tile payloads use gzip with {@code Deflater.BEST_COMPRESSION}.
 * Directories and JSON metadata keep the default level: their best-level
 * compression costs tens of seconds for well under 0.1% of the archive.
 *
 * <p>Other MLT size options were measured and deliberately not enabled: FastPFOR
 * increases size by about 7% and slows encoding substantially, FSST is neutral,
 * forcing string column mappings increases size, and feature id sorting with
 * bundled ids is neutral.
 *
 * <h2>Coordinate conventions</h2>
 *
 * <ul>
 *   <li>Source coordinates are longitude/latitude and are projected to world
 *       coordinates in {@code [0,1]}; latitude clamping matches Planetiler's
 *       {@code getWorldLat(-0.1)} / {@code getWorldLat(1.1)} limits.</li>
 *   <li>A zoom {@code z} scales world coordinates by {@code 2^z}; tiling works in
 *       tile-local coordinates {@code [0,256)} with a {@code 4px} buffer.</li>
 *   <li>Features are simplified with a {@code 0.1px} tolerance below the maximum
 *       zoom and not simplified at the maximum zoom, matching
 *       {@code transformScaledGeometryByZoom(...NOOP...)}.</li>
 *   <li>{@link io.github.pfwikis.tile.TileGeometryCodec} converts tile geometry
 *       to the same integer commands Planetiler's
 *       {@code VectorTile.encodeGeometry(...).unscale()} produces, so both
 *       implementations hand byte-for-byte equivalent geometries to MLT.</li>
 *   <li>Filled tiles use a polygon expanded by {@code 64} extent units
 *       ({@code 4px} buffer) on every side.</li>
 * </ul>
 *
 * <h2>Concurrency</h2>
 *
 * <p>Layers are tiled one at a time so that source geometry of a finished layer
 * can be released before the next (usually larger) layer is processed. Features
 * within a layer are tiled in parallel; work is split further across fill
 * columns and large polygon tiles. Tile encoding runs on a fixed thread pool
 * with a bounded sliding window, while PMTiles writing remains sequential so the
 * tile-id order and run-length compression stay deterministic.
 *
 * <h2>Determinism and parity</h2>
 *
 * <p>Features are finally ordered by source order within each layer; layer names
 * are sorted before MLT encoding. Temporary spilling and parallel tiling do not
 * affect the decoded result. Given identical prepared layers, the output matches
 * the previous Planetiler-based pipeline tile-for-tile (verified by decoding both
 * archives and comparing layer names, feature sets, properties and geometry).
 * Whole-pipeline runs can still differ because upstream label-center generation
 * is not deterministic; that difference exists between two runs of the old
 * implementation as well.
 *
 * <h2>Dependencies</h2>
 *
 * <p>The package uses JTS for geometry, HPPC and RoaringBitmap for compact tile
 * and fill storage, and {@code org.maplibre:mlt:0.0.12} for MLT conversion.
 * Planetiler is a test-scope dependency only (used by parity tests); no runtime
 * class from Planetiler is required.
 */
package io.github.pfwikis.tile;
