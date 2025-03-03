package io.github.pfwikis.util;

import java.awt.Color;
import java.util.function.DoubleFunction;
import java.util.function.DoubleUnaryOperator;

import org.hsluv.HsluvColorConverter;

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

	public static Color brightenBy(Color col, double brighten) {
		return modifyLightness(col, old->old+brighten);
	}

	public static Color darkenTo(Color col, double darken) {
		return modifyLightness(col, old->darken);
	}
	
	public static Color modifyLightness(Color col, DoubleUnaryOperator mod) {
		var c = new HsluvColorConverter();
		c.rgb_r=col.getRed()/255d;
		c.rgb_g=col.getGreen()/255d;
		c.rgb_b=col.getBlue()/255d;
		c.rgbToHsluv();
		
		c.hsluv_l = Math.clamp(mod.applyAsDouble(c.hsluv_l/100d)*100d,0d,100d);
		c.hsluvToRgb();
		return new Color(
			Math.clamp((float)c.rgb_r, 0f, 1f),
			Math.clamp((float)c.rgb_g, 0f, 1f),
			Math.clamp((float)c.rgb_b, 0f, 1f)
		);
	}
}
