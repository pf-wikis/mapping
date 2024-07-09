package io.github.pfwikis.util;

import java.awt.Color;

public class ColorUtil {
	public static void main(String[] args) {
		System.out.println(toHex(new Color(1f,1f,1f,0.6f)));
		System.out.println(fromHex(toHex(new Color(1f,1f,1f,0.6f))));
		System.out.println(toHex(new Color(.1f,.7f,1f,0.9f)));
		System.out.println(fromHex(toHex(new Color(.1f,.7f,1f,0.9f))));
		System.out.println(toHex(new Color(255,0,0)));
		System.out.println(fromHex(toHex(new Color(255,0,0))));
	}
	
	public static String toHex(Color col) {
		var val = "%06X".formatted(0xFFFFFFFF & col.getRGB());
		if(val.startsWith("FF")) val = val.substring(2);
		else val = val.substring(2)+val.substring(0,2);
		for(int i=0;i<val.length();i+=2) {
			if(val.charAt(i) != val.charAt(i+1))
				return "#"+val;
		}
		return "#"+val.replaceAll("(.)\\1", "$1");
	}
	
	public static Color fromHex(String col) {
		col=col.substring(1);
		if(col.length()<6) {
			col=col.replaceAll("(.)", "$1$1");
		}
		if(col.length()==8) {
			var res = fromHex("#"+col.substring(0, 6));
			res = new Color(res.getRed(), res.getGreen(), res.getBlue(), Integer.parseInt(col.substring(6), 16));
			return res;
		}
		return Color.decode("#"+col);
	}

	public static Color darken(Color col, float darken) {
		var hsb = Color.RGBtoHSB(col.getRed(), col.getGreen(), col.getBlue(), null);
		return new Color(Color.HSBtoRGB(hsb[0], hsb[1], Math.clamp(hsb[2]-darken,0f,1f)));
	}
}
