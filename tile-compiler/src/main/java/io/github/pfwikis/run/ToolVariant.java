package io.github.pfwikis.run;

import java.io.IOException;
import java.lang.ProcessBuilder.Redirect;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.util.List;
import java.util.stream.Collectors;

import org.apache.commons.io.IOUtils;

import com.google.common.collect.Lists;

import lombok.Getter;

public enum ToolVariant {
	
	NATIVE {
		@Override
		public boolean isStdInSupported() {
			return true;
		}
	},
	CMD {
		@Override
		public void modifyArguments(List<String> args) {
			args.set(0, args.get(0)+".cmd");
		}
	},
	WSL {
		@Override
		public void modifyArguments(List<String> args) {
			var oArgs = List.copyOf(args);
			args.clear();
			args.add("wsl");
			args.add(oArgs.get(0));
			oArgs.stream()
				.skip(1)
				.map(v->v.replace("\\", "\\\\"))
				.map(v->v.replace("'", "'\\''"))
				.forEach(v->args.add("'"+v+"'"));
		}

		@Override
		public String translateFile(Path p) {
			var wslPath = p.toAbsolutePath().normalize().toString();
			if(wslPath.startsWith("\\\\wsl$\\Ubuntu\\tmp\\")) {
				return wslPath.substring(13).replace("\\", "/");
			}
			var letter = Character.toLowerCase(wslPath.charAt(0));
			return "/mnt/"+letter+wslPath.substring(2).replace("\\", "/");
		}
	},
	WSL_NPM {
		
		@Override
		public String translateFile(Path p) {
			return WSL.translateFile(p);
		}
		
		@Override
		public void modifyArguments(List<String> args) {
			var oArgs = List.copyOf(args);
			args.clear();
			args.addAll(List.of("wsl", "-e", "bash", "-li", "-c"));
			var toCall = oArgs.get(0)+" ";
			toCall+=oArgs.stream()
				.skip(1)
				.map(v->v.replace("\\", "\\\\"))
				.map(v->v.replace("'", "'\\''"))
				.collect(Collectors.joining("' '", "'", "'"));
			args.add(toCall);
		}
	};
	
	public static ToolVariant getFor(String command) {
		return switch(command) {
			case "ogr2ogr" -> getOgr2ogr();
			case "mapshaper" -> getMapshaper();
			case "geojson-polygon-labels" -> getGeojsonPolygonLabels();
			case "qgis_process" -> getQgisProcess();
			case "tippecanoe" -> getTippecanoe();
			case "spritezero" -> getSpritezero();
			default -> throw new IllegalStateException("Unhandled tool "+command);
		};
	}
	
	@Getter(lazy = true)
	private static final ToolVariant ogr2ogr = test("ogr2ogr", "--version");
	@Getter(lazy = true)
	private static final ToolVariant mapshaper = test("mapshaper", "--version");
	@Getter(lazy = true)
	private static final ToolVariant geojsonPolygonLabels = test("geojson-polygon-labels", "--help");
	@Getter(lazy = true)
	private static final ToolVariant qgisProcess = test("qgis_process", "-v");
	@Getter(lazy = true)
	private static final ToolVariant tippecanoe = test("tippecanoe", "-v");
	@Getter(lazy = true)
	private static final ToolVariant spritezero = testOutput("Example", "spritezero", "help");
	
	
	private static ToolVariant test(String... args) {
		for(var variant : ToolVariant.values()) {
			try {
				var cmd = Lists.newArrayList(args);
				variant.modifyArguments(cmd);
				Process proc = new ProcessBuilder()
					.command(cmd)
					.start();
				proc.onExit().join();
				if(proc.exitValue()==0)
					return variant;
			} catch (IOException e) {}
		}
	
		throw new IllegalStateException("Unhandled tool "+args[0]);
	}
	
	private static ToolVariant testOutput(String expected, String... args) {
		for(var variant : ToolVariant.values()) {
			try {
				var cmd = Lists.newArrayList(args);
				variant.modifyArguments(cmd);
				
				Process proc = new ProcessBuilder()
					.command(cmd)
					.redirectOutput(Redirect.PIPE)
					.start();
				proc.onExit().join();
				var result = IOUtils.toString(proc.getInputStream(), StandardCharsets.UTF_8);
				if(result.contains(expected)) {
					return variant;
				}
			} catch (IOException e) {}
		}
		throw new IllegalStateException("Unhandled tool "+args[0]);
	}
	
	public String translateFile(Path p) {
		return p.toString();
	}

	public void modifyArguments(List<String> args) {}

	public boolean isStdInSupported() {
		return false;
	}

}
