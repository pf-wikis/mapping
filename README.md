# PathfinderWiki Golarion Map

This repo contains the source code and project support files for the interactive map of Golarion used by the PathfinderWiki. It contains:

- a Java vector tile generator, which outputs a PMTiles file containing tiles generated from data layers in the [mapping-data repository](https://github.com/pf-wikis/mapping-data)
- the [maplibre-gl-js](https://maplibre.org/maplibre-gl-js/) frontend and related plugins for viewing the PMTiles file as an interactive map
- city and point-of-interest positions as GeoJSON files, and a Java `wiki-downloader` bot that routinely fetches and compiles those coordinates from PathfinderWiki articles
- a [QGIS](https://qgis.org/en/site/) project file for georeferencing source images and modifying mapping-data contents
- [DVC](https://dvc.org/) metadata files for raster source map images, which are stored in separate, secure external storage requiring permission to access

To view the current map, go to https://map.pathfinderwiki.com.

This project is coordinated on the [PathfinderWiki Discord server's](https://discord.gg/xueae4g) [`#golarion-mapping` channel](https://discord.com/channels/499368889176555520/1133657894181671023).

### How to to work with the mapping-data repository

The `kart` command-line tool works very similarly to git, but uses a custom index format to manage geospatial data and present it as a geopackage file instead of the normal filesystem. Kart also integrates with QGIS to create 

To check out the mapping-data repository in geopackage format:

1. Install [Kart](https://kartproject.org), a git-compatible distributed version control tool for geospatial data.
2. Use kart to clone [pf-wikis/mapping-data](https://github.com/pf-wikis/mapping-data) into a directory called `mapping-data` next to the directory containing this repo: `kart clone git@github.com:pf-wikis/mapping-data`
3. Open the QGIS project file located at `sources/qgis.qgs` in this repository. If the `mapping-data` repository is in the same parent path as the `mapping` repository, the project should automatically find and load the GeoPackage data file and GeoJSON location files containing the project's layers.

## How to contribute

For general information about the map project, see [the Map article on PathfinderWiki](https://pathfinderwiki.com/wiki/Map).

### How to modify coordinates of cities and points of interest

You can provide or update the coordinates of cities and points of interest by editing their corresponding PathfinderWiki articles. For locations lacking articles on PathfinderWiki, enter their coordinates on the [PathfinderWiki:Map Locations Without Articles](https://pathfinderwiki.com/wiki/PathfinderWiki:Map_Locations_Without_Articles) project page. A bot routinely fetches and compiles these coordinates from PathfinderWiki into GeoJSON files that are included in the generated map tiles and populate the `cities` and `locations` layers of the QGIS project file.

> **Do not attempt to modify these coordinates in the cities and locations GeoJSON files in this repository, which are automatically overwritten by wiki-sourced data.**

For more information, see [Help:Map on PathfinderWiki](https://pathfinderwiki.com/wiki/Help:Map). To register an account on PathfinderWiki, see [Special:RequestAccount](https://pathfinderwiki.com/wiki/Special:RequestAccount).

### How to modify features with geometry in the mapping-data repository

Features that are polygonal shapes or lines, such as landmasses, bodies of water, rivers, borders, etc., are managed as geospatial data in a separate [Kart](https://kartproject.org) repository, [pf-wikis/mapping-data](https://github.com/pf-wikis/mapping-data). We use [QGIS](https://qgis.org/en/site/) to edit these features.

#### Install QGIS and recommended plugins

[QGIS](https://qgis.org/en/site/about/index.html) is a Geographic Information System (GIS) that allows for precise editing of geographic data. While its interface is somewhat similar to vector drawing tools such as Inkscape or Adobe Illustrator, it is fundamentally a data editing tool and follows different user interface paradaigms that might be unfamiliar if you don't have prior GIS experience. There are several tutorials that review QGIS's basic interface and usage, such as [GeoDelta Labs' 1-hour beginner's guide](https://www.youtube.com/watch?v=NHolzMgaqwE).

To install QGIS:

1. Go to the [QGIS download page](https://qgis.org/en/site/forusers/download.html).
2. Select your operating system.
3. Download the installer for your operating system, or follow the instructions provided for your package manager.

The current LTS version of QGIS is sufficient for editing, and the latest stable version might also be compatible. If in doubt, check with other volunteers in the [PathfinderWiki Discord server's](https://discord.gg/xueae4g) [`#golarion-mapping` channel](https://discord.com/channels/499368889176555520/1133657894181671023). Editing the project file itself is not necessary unless you've added a georeferenced raster image; _see "(Optional) Check out raster source map images"_.

After installing QGIS, launch it and install the **Trackable QGIS Project** plugins required for working on this project.

1. In QGIS, click **Plugins** in the menu, then click **Manage and Install Plugins...**.
2. Click the **All** tab on the left sidebar.
3. Click the **Search...** bar and type _Trackable QGIS Project_.
4. Click **Trackable QGIS Project** in the search results. If it doesn't appear, your device or operating system might not be supported for this project.
5. Click the **Install Plugin** button.

These plugins are also recommended for installation:

- Kart
- Freehand raster georeferencer

#### Install Kart and clone the mapping-data repository

This project uses [Kart](https://kartproject.org), a git-like tool that uses a custom index format to manage and track changes to geospatial data and present it as a GeoPackage file, instead of git's use of a normal filesystem. QGIS can open this GeoPackage file to view and modify its layers.

To check out the mapping-data repository in GeoPackage format:

1. Install [Kart](https://kartproject.org), a git-compatible distributed version control tool for geospatial data.
2. Use the `kart` command to clone the [pf-wikis/mapping-data](https://github.com/pf-wikis/mapping-data) repository into a directory called `mapping-data` next to the directory containing this repo: `kart clone git@github.com:pf-wikis/mapping-data`

The `kart` command-line tool works similarly to git, including commands to fetch, pull, merge, branch, and commit changes to the repository.

To suggest changes you've made to geometry:

1. Fork the `pf-wikis/mapping-data` repository on GitHub.
2. Use `kart` to add your fork to your local repository: `kart remote add REMOTENAME git@github.com:USERNAME/mapping-data`, where `REMOTENAME` can be any name you want to use to refer to your fork, and `USERNAME` is your GitHub username for your fork.
3. Create a new branch: `kart switch -c my-changes`, where `my-changes` can be any valid git branch name.
4. Commit your changes: `kart commit -m "Changes to map data"`, where `Changes to map data` is your commit message.
5. Push your changes: `kart push -u REMOTENAME my-changes`.
6. [Create a pull request in GitHub](https://docs.github.com/en/pull-requests/collaborating-with-pull-requests/proposing-changes-to-your-work-with-pull-requests/creating-a-pull-request) for your fork's branch to the `main` branch of `pf-wikis/mapping-data`.

The Kart plugin for QGIS can visualize differences between your working changes and the branch you're working on, or between commits within the repository. Including screenshots of these differences that depict your requested changes can help illustrate them to reviewers. You can also use the plugin to manage your local clone of the repository. For details, see the [plugin's documentation](https://github.com/koordinates/kart-qgis-plugin/blob/main/docs/index.md).

#### (Optional) Check out raster source map images

Geometry in the `mapping-data` repository is created by project volunteers who attempt to georeference raster maps published by Paizo and interpret features on those maps as vector layer data in QGIS. Paizo owns the copyright of these raster source map images, which **cannot** be publicly shared under the Fan Content Policy or other licenses. The mapping project provides access to these images only to volunteers who have individually requested and been granted access.

If you have been granted permission to access these raster maps, you can download them by following these steps:

1. Download and install [DVC](https://dvc.org/).
2. Run ```dvc remote modify --local webdav password THEPASSWORD```
3. Within this repository's root, run ```dvc install```. This sets up automatic git hooks so that DVC pushes content when you push with git.
4. Run ```dvc pull``` to download the images. If this is your first time pulling images, you will have to authenticate with the account that has been granted access to the external map storage.

Georeferencing these images is a complex and subjective process that requires coordination with other volunteers. To request access or coordinate georeferencing efforts, please join the [PathfinderWiki Discord server's](https://discord.gg/xueae4g) [`#golarion-mapping` channel](https://discord.com/channels/499368889176555520/1133657894181671023).

#### Edit geometry in QGIS

Open the QGIS project file located at `sources/qgis.qgs` in this repository. If everything is correct, the QGIS project should automatically locate the GeoPackage file containing feature geometry layers.

If QGIS warns about missing raster layers in a **Handle Unavailable Layers** dialog, click **Keep Unavailable Layers**; they aren't required. _See "(Optional) Check out raster source map images"._

The project contains separate polygon, line, and point layers for features. Labels for most features are stored in their attribute table (<kbd>F6</kbd> or **Layer->Open attribute table**). The `borders` layer defines national borders, the `districts` layer defines sub-national regions, and the `labels` layer defines other types of regions that require labels, including ocean regions and island chains. The tile generation tool automatically smooths the lines or shapes for these feature types.

The `rivers` layer attributes table contains the width used for the feature. Rivers might be intentionally segemented into multiple connected but distinct features to apply different widths. The tile generator automatically smooths rivers and tapers their widths near their ends. As such, you should extend the lines of river features significantly into the geometry of any body of water that they empty into.

> **Remember to save changes to geometry layers! It is easy to lose work in QGIS if you do not save changes to layers.**

## Locally build the mapping project tiles and website

If you're interested in contributing to development of the map website's user interface or tile-generation process, we recommend coordinating with other mapping project members on the [PathfinderWiki Discord server's](https://discord.gg/xueae4g) [`#golarion-mapping` channel](https://discord.com/channels/499368889176555520/1133657894181671023).

This project contains a Java `tile-compiler` tool that generates layered vector tiles from the `mapping-data` repository's data and compiles them into a single Protomaps tiles (PMTiles) file. It also contains a `frontend` that renders the PMTiles file using [maplibre-gl-js](https://maplibre.org/maplibre-gl-js/) in a TypeScript project.

> The map website is built on a Debian-based Linux system. If you use Windows or macOS, building the website locally might be more complex.

To locally build the website to test changes to mapping data, you must first build the PMTiles file from the mapping data, then build the maplibre-gl-js frontend and run the local development server. This can require installing several dependencies, including Java SDK 21+, Node.js, and Tippecanoe.

See also the [GitHub Actions Dockerfile](https://github.com/pf-wikis/mapping/blob/main/.github/compiler-docker/Dockerfile), which includes the commands used to automate installing requirements on a Debian-based Linux operating system for testing and publishing the website.

1.  Install at least version 21 of either the Oracle or OpenJDK Java JDK; a compatible version of Maven; and Node.js and npm. Dependencies and installation methods depend on your operating system. On Linux, relevant packages include `openjdk-21-jdk` or `java-21-openjdk-devel`, `maven` or `maven-openjdk21`, `nodejs`, and `npm`.
2.  Install [felt/tippecanoe](https://github.com/felt/tippecanoe). Dependencies and installation methods depend on your operating system. On Linux, required packages include `libsqlite3x-devel` and `zlib-devel`.
3.  Install GDAL and GRASS. Dependencies and installation methods depend on your operating system. On Linux, required packages include `gdal` and `grass`.
4.  Install Javascript prerequisites for the tile compiler using npm: `npm install mapshaper geojson-polygon-labels curve-interpolator@3.0.1 @indoorequal/spritezero-cli`
5.  Install [kart](https://kartproject.org/).
6.  Clone this `pf-wikis/mapping` repo with `git`: `git clone https://github.com/pf-wikis/mapping`.
7.  Clone the `pf-wikis/mapping-data` kart repository into a directory called `mapping-data` next to the directory containing the `mapping` repo: `kart clone https://github.com/pf-wikis/mapping-data`.
8.  Install and run the map tile compiler from the `tile-compiler` directory:
    ```
    cd mapping/tile-compiler
    ./run.sh
    ```

    If this step fails, examine any error messages for information about potentially missing dependencies or errors in the source data.

    If you have multiple versions of the JDK installed on your system, you might need to specify a compatible JDK using the `JAVA_HOME` environment variable. For example, when invoking the `run.sh` script, use:

    ```
    JAVAHOME=/usr/lib/jvm/java-21-openjdk-21.0.3.0.9-1.fc40.x86_64/bin ./run.sh
    ```

    where `/usr/lib/jvm/java-21-openjdk-21.0.3.0.9-1.fc40.x86_64/bin` is the path to a compatible JDK installation's `java` and `javac` binaries.
9.  Install frontend dependencies using `npm install` from the `frontend` directory:
    ```
    cd ../frontend
    npm i
    ```
10. Run the frontend from the `frontend` directory:
    ```
    npm start
    ```

    This launches a local webserver on a non-privileged port, such as `http://localhost:5173/`. Open this URL in a web browser to view the locally built map.

## Acknowledgments

This mapping project uses trademarks and/or copyrights owned by Paizo Inc., used under Paizo's Fan Content Policy ([paizo.com/licenses/fancontent](https://paizo.com/licenses/fancontent)). This mapping project is not published, endorsed, or specifically approved by Paizo. For more information about Paizo Inc. and Paizo products, visit [paizo.com](https://paizo.com).

Significant data in this project is based on GIS data first compiled by [John Mechalas](https://www.dungeonetics.com/golarion-geography/index.html) and contributions to that project from Oznogon, who produced and previously hosted the [first interactive map](https://oznogon.com/golarion-tile) from that data.

Several contributors have provided or updated coordinates and geometry in this project, as visible in the history of this repository and the pf-wikis/mapping-data repository. Others have contributed city and point-of-interest positions by editing PathfinderWiki articles, as visible in the History tab of each city and location's related articles or the [PathfinderWiki:Map Locations Without Articles](https://pathfinderwiki.com/wiki/PathfinderWiki:Map_Locations_Without_Articles) project page.
