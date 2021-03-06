/*******************************************************************************
 * Copyright 2011 See AUTHORS file.
 * 
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 * 
 *   http://www.apache.org/licenses/LICENSE-2.0
 * 
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 ******************************************************************************/

package com.badlogic.gdx.graphics.g2d.freetype;

import java.nio.ByteBuffer;

import com.badlogic.gdx.Gdx;
import com.badlogic.gdx.files.FileHandle;
import com.badlogic.gdx.graphics.Color;
import com.badlogic.gdx.graphics.Pixmap;
import com.badlogic.gdx.graphics.Pixmap.Blending;
import com.badlogic.gdx.graphics.Pixmap.Format;
import com.badlogic.gdx.graphics.Texture;
import com.badlogic.gdx.graphics.Texture.TextureFilter;
import com.badlogic.gdx.graphics.g2d.BitmapFont;
import com.badlogic.gdx.graphics.g2d.BitmapFont.BitmapFontData;
import com.badlogic.gdx.graphics.g2d.BitmapFont.Glyph;
import com.badlogic.gdx.graphics.g2d.PixmapPacker;
import com.badlogic.gdx.graphics.g2d.PixmapPacker.Page;
import com.badlogic.gdx.graphics.g2d.TextureRegion;
import com.badlogic.gdx.graphics.g2d.freetype.FreeType.Bitmap;
import com.badlogic.gdx.graphics.g2d.freetype.FreeType.Face;
import com.badlogic.gdx.graphics.g2d.freetype.FreeType.GlyphMetrics;
import com.badlogic.gdx.graphics.g2d.freetype.FreeType.GlyphSlot;
import com.badlogic.gdx.graphics.g2d.freetype.FreeType.Library;
import com.badlogic.gdx.graphics.g2d.freetype.FreeType.SizeMetrics;
import com.badlogic.gdx.graphics.g2d.freetype.FreeType.Stroker;
import com.badlogic.gdx.graphics.glutils.PixmapTextureData;
import com.badlogic.gdx.math.MathUtils;
import com.badlogic.gdx.math.Rectangle;
import com.badlogic.gdx.utils.Array;
import com.badlogic.gdx.utils.Disposable;
import com.badlogic.gdx.utils.GdxRuntimeException;

/** Generates {@link BitmapFont} and {@link BitmapFontData} instances from TrueType font files.</p>
 * 
 * Usage example:
 * 
 * <pre>
 * FreeTypeFontGenerator gen = new FreeTypeFontGenerator(Gdx.files.internal(&quot;myfont.ttf&quot;));
 * BitmapFont font = gen.generateFont(16);
 * gen.dispose();
 * </pre>
 * 
 * The generator has to be disposed once it is no longer used. The returned {@link BitmapFont} instances are managed by the user
 * and have to be disposed as usual.
 * 
 * @author mzechner */
public class FreeTypeFontGenerator implements Disposable {
	public static final String DEFAULT_CHARS = "ABCDEFGHIJKLMNOPQRSTUVWXYZabcdefghijklmnopqrstuvwxyz1234567890\"!`?'.,;:()[]{}<>|/@\\^$-%+=#_&~*\u0080\u0081\u0082\u0083\u0084\u0085\u0086\u0087\u0088\u0089\u008A\u008B\u008C\u008D\u008E\u008F\u0090\u0091\u0092\u0093\u0094\u0095\u0096\u0097\u0098\u0099\u009A\u009B\u009C\u009D\u009E\u009F\u00A0\u00A1\u00A2\u00A3\u00A4\u00A5\u00A6\u00A7\u00A8\u00A9\u00AA\u00AB\u00AC\u00AD\u00AE\u00AF\u00B0\u00B1\u00B2\u00B3\u00B4\u00B5\u00B6\u00B7\u00B8\u00B9\u00BA\u00BB\u00BC\u00BD\u00BE\u00BF\u00C0\u00C1\u00C2\u00C3\u00C4\u00C5\u00C6\u00C7\u00C8\u00C9\u00CA\u00CB\u00CC\u00CD\u00CE\u00CF\u00D0\u00D1\u00D2\u00D3\u00D4\u00D5\u00D6\u00D7\u00D8\u00D9\u00DA\u00DB\u00DC\u00DD\u00DE\u00DF\u00E0\u00E1\u00E2\u00E3\u00E4\u00E5\u00E6\u00E7\u00E8\u00E9\u00EA\u00EB\u00EC\u00ED\u00EE\u00EF\u00F0\u00F1\u00F2\u00F3\u00F4\u00F5\u00F6\u00F7\u00F8\u00F9\u00FA\u00FB\u00FC\u00FD\u00FE\u00FF";
	final Library library;
	final Face face;
	final String filePath;
	boolean bitmapped = false;

	/** The maximum texture size allowed by generateData, when storing in a texture atlas. Multiple texture pages will be created if
	 * necessary. */
	private static int maxTextureSize = 1024;

	/** A hint to scale the texture as needed, without capping it at any maximum size */
	public static final int NO_MAXIMUM = -1;

	/** Sets the maximum size that will be used when generating texture atlases for glyphs with <tt>generateData()</tt>. The default
	 * is 1024. By specifying NO_MAXIMUM, the texture atlas will scale as needed.
	 * 
	 * The power-of-two square texture size will be capped to the given <tt>texSize</tt>. It's recommended that a power-of-two
	 * value be used here.
	 * 
	 * Multiple pages may be used to fit all the generated glyphs. You can query the resulting number of pages by calling
	 * <tt>bitmapFont.getRegions().length</tt> or <tt>freeTypeBitmapFontData.getTextureRegions().length</tt>.
	 * 
	 * If PixmapPacker is specified when calling generateData, this parameter is ignored.
	 * 
	 * @param texSize the maximum texture size for one page of glyphs */
	public static void setMaxTextureSize (int texSize) {
		maxTextureSize = texSize;
	}

	/** Returns the maximum texture size that will be used by generateData() when creating a texture atlas for the glyphs.
	 * @return the power-of-two max texture size */
	public static int getMaxTextureSize () {
		return maxTextureSize;
	}

	/** Creates a new generator from the given TrueType font file. Throws a {@link GdxRuntimeException} in case loading did not
	 * succeed.
	 * @param font the {@link FileHandle} to the TrueType font file */
	public FreeTypeFontGenerator (FileHandle font) {
		filePath = font.pathWithoutExtension();
		library = FreeType.initFreeType();
		if (library == null) throw new GdxRuntimeException("Couldn't initialize FreeType");
		face = library.newFace(font, 0);
		if (face == null) throw new GdxRuntimeException("Couldn't create face for font '" + font + "'");
		if (checkForBitmapFont()) {
			return;
		}
		if (!face.setPixelSizes(0, 15)) throw new GdxRuntimeException("Couldn't set size for font '" + font + "'");
	}

	private boolean checkForBitmapFont () {
		if (((face.getFaceFlags() & FreeType.FT_FACE_FLAG_FIXED_SIZES) == FreeType.FT_FACE_FLAG_FIXED_SIZES)
			&& ((face.getFaceFlags() & FreeType.FT_FACE_FLAG_HORIZONTAL) == FreeType.FT_FACE_FLAG_HORIZONTAL)) {
			if (face.loadChar(32, FreeType.FT_LOAD_DEFAULT)) {
				GlyphSlot slot = face.getGlyph();
				if (slot.getFormat() == 1651078259) {
					bitmapped = true;
				}
			}
		}
		return bitmapped;
	}

	/** Generates a new {@link BitmapFont}, containing glyphs for the given characters. The size is expressed in pixels. Throws a
	 * GdxRuntimeException in case the font could not be generated. Using big sizes might cause such an exception. All characters
	 * need to fit onto a single texture.
	 * @param size the size in pixels
	 * @param characters the characters the font should contain
	 * @param flip whether to flip the font vertically, see {@link BitmapFont#BitmapFont(FileHandle, TextureRegion, boolean)}
	 * @deprecated use {@link #generateFont(FreeTypeFontParameter)} instead */
	public BitmapFont generateFont (int size, String characters, boolean flip) {
		FreeTypeBitmapFontData data = generateData(size, characters, flip, null);
		BitmapFont font = new BitmapFont(data, data.getTextureRegions(), false);
		font.setOwnsTexture(true);
		return font;
	}

	/** Generates a new {@link BitmapFont}. The size is expressed in pixels. Throws a GdxRuntimeException in case the font could not
	 * be generated. Using big sizes might cause such an exception. All characters need to fit onto a single texture.
	 * 
	 * @param size the size of the font in pixels
	 * @deprecated use {@link #generateFont(FreeTypeFontParameter)} instead */
	public BitmapFont generateFont (int size) {
		return generateFont(size, DEFAULT_CHARS, false);
	}

	/** Generates a new {@link BitmapFont}. The size is expressed in pixels. Throws a GdxRuntimeException in case the font could not
	 * be generated. Using big sizes might cause such an exception. All characters need to fit onto a single texture.
	 * 
	 * @param parameter configures how the font is generated */
	public BitmapFont generateFont (FreeTypeFontParameter parameter) {
		FreeTypeBitmapFontData data = generateData(parameter);
		BitmapFont font = new BitmapFont(data, data.getTextureRegions(), false);
		font.setOwnsTexture(true);
		return font;
	}

	/** Uses ascender and descender of font to calculate real height that makes all glyphs to fit in given pixel size. Source:
	 * http://nothings.org/stb/stb_truetype.h / stbtt_ScaleForPixelHeight */
	public int scaleForPixelHeight (int height) {
		if (!bitmapped && !face.setPixelSizes(0, height)) throw new GdxRuntimeException("Couldn't set size for font");
		SizeMetrics fontMetrics = face.getSize().getMetrics();
		int ascent = FreeType.toInt(fontMetrics.getAscender());
		int descent = FreeType.toInt(fontMetrics.getDescender());
		return height * height / (ascent - descent);
	}
	
	/** Uses max advance, ascender and descender of font to calculate real height that makes any n glyphs to fit in given pixel width. 
	 * @param width the max width to fit (in pixels)
	 * @param numChars max number of characters that to fill width */
	public int scaleForPixelWidth(int width, int numChars) {
		SizeMetrics fontMetrics = face.getSize().getMetrics();
		int advance = FreeType.toInt(fontMetrics.getMaxAdvance());
		int ascent = FreeType.toInt(fontMetrics.getAscender());
		int descent = FreeType.toInt(fontMetrics.getDescender());
		int unscaledHeight = ascent - descent;
		int height = unscaledHeight * width / (advance * numChars);
		if (!bitmapped && !face.setPixelSizes(0, height)) throw new GdxRuntimeException("Couldn't set size for font");
		return height;
	}
	
	/** Uses max advance, ascender and descender of font to calculate real height that makes any n glyphs to fit in given pixel width and height. 
	 * @param width the max width to fit (in pixels)
	 * @param height the max height to fit (in pixels)
	 * @param numChars max number of characters that to fill width */
	public int scaleToFitSquare(int width, int height, int numChars) {
		return Math.min(scaleForPixelHeight(height), scaleForPixelWidth(width, numChars));
	}

	public class GlyphAndBitmap {
		public Glyph glyph;
		public Bitmap bitmap;
	}

	/** Returns null if glyph was not found. If there is nothing to render, for example with various space characters, then bitmap
	 * is null. */
	public GlyphAndBitmap generateGlyphAndBitmap (int c, int size, boolean flip) {
		if (!bitmapped && !face.setPixelSizes(0, size)) throw new GdxRuntimeException("Couldn't set size for font");

		SizeMetrics fontMetrics = face.getSize().getMetrics();
		int baseline = FreeType.toInt(fontMetrics.getAscender());

		// Check if character exists in this font.
		// 0 means 'undefined character code'
		if (face.getCharIndex(c) == 0) {
			return null;
		}

		// Try to load character
		if (!face.loadChar(c, FreeType.FT_LOAD_DEFAULT)) {
			throw new GdxRuntimeException("Unable to load character!");
		}

		GlyphSlot slot = face.getGlyph();

		// Try to render to bitmap
		Bitmap bitmap;
		if (bitmapped) {
			bitmap = slot.getBitmap();
		} else if (!slot.renderGlyph(FreeType.FT_RENDER_MODE_LIGHT)) {
			bitmap = null;
		} else {
			bitmap = slot.getBitmap();
		}

		GlyphMetrics metrics = slot.getMetrics();

		Glyph glyph = new Glyph();
		if (bitmap != null) {
			glyph.width = bitmap.getWidth();
			glyph.height = bitmap.getRows();
		} else {
			glyph.width = 0;
			glyph.height = 0;
		}
		glyph.xoffset = slot.getBitmapLeft();
		glyph.yoffset = flip ? -slot.getBitmapTop() + baseline : -(glyph.height - slot.getBitmapTop()) - baseline;
		glyph.xadvance = FreeType.toInt(metrics.getHoriAdvance());
		glyph.srcX = 0;
		glyph.srcY = 0;
		glyph.id = c;

		GlyphAndBitmap result = new GlyphAndBitmap();
		result.glyph = glyph;
		result.bitmap = bitmap;
		return result;
	}

	/** Generates a new {@link BitmapFontData} instance, expert usage only. Throws a GdxRuntimeException in case something went
	 * wrong.
	 * @param size the size in pixels */
	public FreeTypeBitmapFontData generateData (int size) {
		return generateData(size, DEFAULT_CHARS, false, null);
	}

	/** Generates a new {@link BitmapFontData} instance, expert usage only. Throws a GdxRuntimeException in case something went
	 * wrong.
	 * 
	 * @param size the size in pixels
	 * @param characters the characters the font should contain
	 * @param flip whether to flip the font vertically, see {@link BitmapFont#BitmapFont(FileHandle, TextureRegion, boolean)}
	 * @deprecated use {@link #generateData(FreeTypeFontParameter)} instead */
	public FreeTypeBitmapFontData generateData (int size, String characters, boolean flip) {
		return generateData(size, characters, flip, null);
	}

	/** Generates a new {@link BitmapFontData} instance, expert usage only. Throws a GdxRuntimeException in case something went
	 * wrong.
	 * 
	 * @param size the size in pixels
	 * @param characters the characters the font should contain
	 * @param flip whether to flip the font vertically, see {@link BitmapFont#BitmapFont(FileHandle, TextureRegion, boolean)}
	 * @param packer the optional PixmapPacker to use
	 * @deprecated use {@link #generateData(FreeTypeFontParameter)} instead */
	public FreeTypeBitmapFontData generateData (int size, String characters, boolean flip, PixmapPacker packer) {
		FreeTypeFontParameter parameter = new FreeTypeFontParameter();
		parameter.size = size;
		parameter.characters = characters;
		parameter.flip = flip;
		parameter.packer = packer;
		return generateData(parameter);
	}

	/** Generates a new {@link BitmapFontData} instance, expert usage only. Throws a GdxRuntimeException in case something went
	 * wrong.
	 * 
	 * @param parameter configures how the font is generated */
	public FreeTypeBitmapFontData generateData (FreeTypeFontParameter parameter) {
		parameter = parameter == null ? new FreeTypeFontParameter() : parameter;

		FreeTypeBitmapFontData data = new FreeTypeBitmapFontData();
		if (!bitmapped && !face.setPixelSizes(0, parameter.size))
			throw new GdxRuntimeException("Couldn't set size for font");

		// set general font data
		SizeMetrics fontMetrics = face.getSize().getMetrics();
		data.flipped = parameter.flip;
		data.ascent = FreeType.toInt(fontMetrics.getAscender());
		data.descent = FreeType.toInt(fontMetrics.getDescender());
		data.lineHeight = FreeType.toInt(fontMetrics.getHeight());
		float baseLine = data.ascent;

		// if bitmapped
		if (bitmapped && (data.lineHeight == 0)) {
			for (int c = 32; c < (32 + face.getNumGlyphs()); c++) {
				if (face.loadChar(c, FreeType.FT_LOAD_DEFAULT)) {
					int lh = FreeType.toInt(face.getGlyph().getMetrics().getHeight());
					data.lineHeight = (lh > data.lineHeight) ? lh : data.lineHeight;
				}
			}
		}

		// determine space width and set glyph
		if (face.loadChar(' ', FreeType.FT_LOAD_DEFAULT)) {
			data.spaceWidth = FreeType.toInt(face.getGlyph().getMetrics().getHoriAdvance());
		} else {
			data.spaceWidth = face.getMaxAdvanceWidth(); // FIXME possibly very wrong :)
		}
		Glyph spaceGlyph = new Glyph();
		spaceGlyph.xadvance = (int)data.spaceWidth;
		spaceGlyph.id = (int)' ';
		data.setGlyph(' ', spaceGlyph);

		// determine x-height
		for (char xChar : BitmapFont.xChars) {
			if (!face.loadChar(xChar, FreeType.FT_LOAD_DEFAULT)) continue;
			data.xHeight = FreeType.toInt(face.getGlyph().getMetrics().getHeight());
			break;
		}
		if (data.xHeight == 0) throw new GdxRuntimeException("No x-height character found in font");
		for (char capChar : BitmapFont.capChars) {
			if (!face.loadChar(capChar, FreeType.FT_LOAD_DEFAULT)) continue;
			data.capHeight = FreeType.toInt(face.getGlyph().getMetrics().getHeight());
			break;
		}

		// determine cap height
		if (!bitmapped && data.capHeight == 1) throw new GdxRuntimeException("No cap character found in font");
		data.ascent = data.ascent - data.capHeight;
		data.down = -data.lineHeight;
		if (parameter.flip) {
			data.ascent = -data.ascent;
			data.down = -data.down;
		}

		boolean ownsAtlas = false;

		PixmapPacker packer = parameter.packer;

		if (packer == null) {
			// generate the glyphs
			int maxGlyphHeight = (int)Math.ceil(data.lineHeight);
			int pageWidth = MathUtils
				.nextPowerOfTwo((int)Math.sqrt(maxGlyphHeight * maxGlyphHeight * parameter.characters.length()));

			if (maxTextureSize > 0) pageWidth = Math.min(pageWidth, maxTextureSize);

			ownsAtlas = true;
			packer = new PixmapPacker(pageWidth, pageWidth, Format.RGBA8888, 2, false);
		}

		// to minimize collisions we'll use this format : pathWithoutExtension_size[_flip]_glyph
		String packPrefix = ownsAtlas ? "" : (filePath + '_' + parameter.size + (parameter.flip ? "_flip_" : '_'));

		Stroker stroker = null;
		if (parameter.borderWidth > 0) {
			stroker = library.createStroker();
			stroker.set((int)(parameter.borderWidth * 64f),
					parameter.borderStraight ? FreeType.FT_STROKER_LINECAP_BUTT : FreeType.FT_STROKER_LINECAP_ROUND,
					parameter.borderStraight ? FreeType.FT_STROKER_LINEJOIN_MITER_FIXED : FreeType.FT_STROKER_LINEJOIN_ROUND, 0);
		}

		for (int i = 0; i < parameter.characters.length(); i++) {
			char c = parameter.characters.charAt(i);
			if (!face.loadChar(c, FreeType.FT_LOAD_DEFAULT)) {
				Gdx.app.log("FreeTypeFontGenerator", "Couldn't load char '" + c + "'");
				continue;
			}
			GlyphSlot slot = face.getGlyph();
			com.badlogic.gdx.graphics.g2d.freetype.FreeType.Glyph mainGlyph = slot.getGlyph();
			try {
				mainGlyph.toBitmap(FreeType.FT_RENDER_MODE_NORMAL);
			} catch (GdxRuntimeException e) {
				mainGlyph.dispose();
				Gdx.app.log("FreeTypeFontGenerator", "Couldn't render char '" + c + "'");
				continue;
			}
			Bitmap mainBitmap = mainGlyph.getBitmap();
			Pixmap mainPixmap = mainBitmap.getPixmap(Format.RGBA8888, parameter.color);

			if (parameter.borderWidth > 0 || parameter.shadowOffsetX != 0 || parameter.shadowOffsetY != 0) {
				com.badlogic.gdx.graphics.g2d.freetype.FreeType.Glyph borderGlyph = mainGlyph;
				Bitmap borderBitmap = mainBitmap;

				if (parameter.borderWidth > 0) {
					//execute stroker; this generates a glyph "extended" along the outline
					borderGlyph = slot.getGlyph();
					borderGlyph.strokeBorder(stroker, false);
					borderGlyph.toBitmap(FreeType.FT_RENDER_MODE_NORMAL);
					borderBitmap = borderGlyph.getBitmap();

					//render border (pixmap is bigger than main)
					Pixmap borderPixmap = borderBitmap.getPixmap(Format.RGBA8888, parameter.borderColor);
					//draw main glyph on top of border
					borderPixmap.drawPixmap(mainPixmap, mainGlyph.getLeft() - borderGlyph.getLeft(),
						-(mainGlyph.getTop() - borderGlyph.getTop()));
					mainPixmap.dispose();
					mainGlyph.dispose();
					mainPixmap = borderPixmap;
					mainGlyph = borderGlyph;
				}
				if (parameter.shadowOffsetX != 0 || parameter.shadowOffsetY != 0) {
					//render the shadow
					Pixmap shadowPixmapSrc = borderBitmap.getPixmap(Format.RGBA8888, parameter.shadowColor);
					//create a new bigger Pixmap with shadowOffset applied, and draw shadow glyph
					Pixmap shadowPixmap = new Pixmap(shadowPixmapSrc.getWidth() + Math.abs(parameter.shadowOffsetX),
						shadowPixmapSrc.getHeight() + Math.abs(parameter.shadowOffsetY), Format.RGBA8888);
					Blending blending = Pixmap.getBlending();
					Pixmap.setBlending(Blending.None);
					shadowPixmap.drawPixmap(shadowPixmapSrc, Math.max(parameter.shadowOffsetX, 0), Math.max(parameter.shadowOffsetY, 0));
					Pixmap.setBlending(blending);
					//draw main glyph (with border) on top of shadow
					shadowPixmap.drawPixmap(mainPixmap, Math.max(-parameter.shadowOffsetX, 0), Math.max(-parameter.shadowOffsetY, 0));
					mainPixmap.dispose();
					mainPixmap = shadowPixmap;
				}
			}

			GlyphMetrics metrics = slot.getMetrics();
			Glyph glyph = new Glyph();
			glyph.id = c;
			glyph.width = mainPixmap.getWidth();
			glyph.height = mainPixmap.getHeight();
			glyph.xoffset = mainGlyph.getLeft();
			glyph.yoffset = parameter.flip ? -mainGlyph.getTop() + (int)baseLine : -(glyph.height - mainGlyph.getTop())
				- (int)baseLine;
			glyph.xadvance = FreeType.toInt(metrics.getHoriAdvance()) + (int)parameter.borderWidth;

			if (bitmapped) {
				mainPixmap.setColor(Color.CLEAR);
				mainPixmap.fill();
				ByteBuffer buf = mainBitmap.getBuffer();
				for (int h = 0; h < glyph.height; h++) {
					int idx = h * mainBitmap.getPitch();
					for (int w = 0; w < (glyph.width + glyph.xoffset); w++) {
						int bit = (buf.get(idx + (w / 8)) >>> (7 - (w % 8))) & 1;
						mainPixmap.drawPixel(w, h, ((bit == 1) ? Color.WHITE.toIntBits() : Color.CLEAR.toIntBits()));
					}
				}

			}

			String name = packPrefix + c;
			Rectangle rect = packer.pack(name, mainPixmap);

			// determine which page it was packed into
			int pIndex = packer.getPageIndex(name);
			if (pIndex == -1) // we should not get here
				throw new IllegalStateException("packer was not able to insert '" + name + "' into a page");

			glyph.page = pIndex;
			glyph.srcX = (int)rect.x;
			glyph.srcY = (int)rect.y;

			data.setGlyph(c, glyph);
			mainPixmap.dispose();
			mainGlyph.dispose();
		}

		if (stroker != null) {
			stroker.dispose();
		}

		// generate kerning
		if (parameter.kerning) {
			for (int i = 0; i < parameter.characters.length(); i++) {
				for (int j = 0; j < parameter.characters.length(); j++) {
					char firstChar = parameter.characters.charAt(i);
					Glyph first = data.getGlyph(firstChar);
					if (first == null) continue;
					char secondChar = parameter.characters.charAt(j);
					Glyph second = data.getGlyph(secondChar);
					if (second == null) continue;
					int kerning = face.getKerning(face.getCharIndex(firstChar),
						face.getCharIndex(secondChar), 0);
					if (kerning == 0) continue;
					first.setKerning(secondChar, FreeType.toInt(kerning));
				}
			}
		}
		if (ownsAtlas) {
			Array<Page> pages = packer.getPages();
			data.regions = new TextureRegion[pages.size];

			for (int i = 0; i < pages.size; i++) {
				Page p = pages.get(i);

				Texture tex = new Texture(new PixmapTextureData(p.getPixmap(), p.getPixmap().getFormat(), parameter.genMipMaps,
					false, true)) {
					@Override
					public void dispose () {
						super.dispose();
						getTextureData().consumePixmap().dispose();
					}
				};
				tex.setFilter(parameter.minFilter, parameter.magFilter);

				data.regions[i] = new TextureRegion(tex);
			}
		}
		return data;
	}

	/** Cleans up all resources of the generator. Call this if you no longer use the generator. */
	@Override
	public void dispose () {
		face.dispose();
		library.dispose();
	}

	/** {@link BitmapFontData} used for fonts generated via the {@link FreeTypeFontGenerator}. The texture storing the glyphs is held
	 * in memory, thus the {@link #getImagePaths()} and {@link #getFontFile()} methods will return null.
	 * @author mzechner */
	public static class FreeTypeBitmapFontData extends BitmapFontData {
		TextureRegion[] regions;

		/** Returns the first texture region. Use getTextureRegions() instead
		 * @return the first texture region in the array
		 * @deprecated use getTextureRegions() instead */
		@Deprecated
		public TextureRegion getTextureRegion () {
			return regions[0];
		}

		public TextureRegion[] getTextureRegions () {
			return regions;
		}
	}

	/** Parameter container class that helps configure how {@link FreeTypeBitmapFontData} and {@link BitmapFont} instances are
	 * generated.
	 * 
	 * The packer field is for advanced usage, where it is necessary to pack multiple BitmapFonts (i.e. styles, sizes, families)
	 * into a single Texture atlas. If no packer is specified, the generator will use its own PixmapPacker to pack the glyphs into
	 * a power-of-two sized texture, and the resulting {@link FreeTypeBitmapFontData} will have a valid {@link TextureRegion} which
	 * can be used to construct a new {@link BitmapFont}.
	 * 
	 * @author siondream */
	public static class FreeTypeFontParameter {
		/** The size in pixels */
		public int size = 16;
		/** Foreground color (required for non-black borders) */
		public Color color = Color.WHITE;
		/** Border width in pixels, 0 to disable */
		public float borderWidth = 0;
		/** Border color; only used if borderWidth > 0 */
		public Color borderColor = Color.BLACK;
		/** true for straight (mitered), false for rounded borders */
		public boolean borderStraight = false;
		/** Offset of text shadow on X axis in pixels, 0 to disable */
		public int shadowOffsetX = 0;
		/** Offset of text shadow on Y axis in pixels, 0 to disable */
		public int shadowOffsetY = 0;
		/** Shadow color; only used if shadowOffset > 0 */
		public Color shadowColor = new Color(0, 0, 0, 0.75f);
		/** The characters the font should contain */
		public String characters = DEFAULT_CHARS;
		/** Whether the font should include kerning */
		public boolean kerning = true;
		/** The optional PixmapPacker to use */
		public PixmapPacker packer = null;
		/** Whether to flip the font vertically */
		public boolean flip = false;
		/** Whether or not to generate mip maps for the resulting texture */
		public boolean genMipMaps = false;
		/** Minification filter */
		public TextureFilter minFilter = TextureFilter.Nearest;
		/** Magnification filter */
		public TextureFilter magFilter = TextureFilter.Nearest;
	}
}
