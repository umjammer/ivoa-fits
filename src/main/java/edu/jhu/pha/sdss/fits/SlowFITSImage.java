/**
 * ID:            $Id: SlowFITSImage.java,v 1.2 2004/07/23 18:52:35 carliles Exp $
 * Revision:      $Revision: 1.2 $
 * Date/time:     $Date: 2004/07/23 18:52:35 $
 */

package edu.jhu.pha.sdss.fits;

import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.Point;
import java.awt.Rectangle;
import java.awt.Transparency;
import java.awt.color.ColorSpace;
import java.awt.image.BufferedImage;
import java.awt.image.ColorModel;
import java.awt.image.ComponentColorModel;
import java.awt.image.DataBuffer;
import java.awt.image.DataBufferUShort;
import java.awt.image.ImageObserver;
import java.awt.image.ImageProducer;
import java.awt.image.Raster;
import java.awt.image.RenderedImage;
import java.awt.image.SampleModel;
import java.awt.image.WritableRaster;
import java.io.File;
import java.io.IOException;
import java.net.URL;
import java.util.Hashtable;
import java.util.Vector;

import nom.tam.fits.BasicHDU;
import nom.tam.fits.Fits;
import nom.tam.fits.FitsException;
import nom.tam.fits.ImageHDU;
import nom.tam.fits.header.Bitpix;
import vavi.util.Debug;
import vavi.util.StringUtil;


/**
 * SlowFITSImage.
 *
 * @author carliles
 * @version 1.2  2004/07/23 18:52:35  carliles SlowFITSImage is done.
 *          1.1  2004/07/22 22:29:08  carliles Added "low" memory consumption SlowFITSImage.
 *          1.19 2004/07/21 22:24:57  carliles Removed some commented crap.
 *
 *          1.18 2004/07/21 18:03:55  carliles Added asinh with sigma estimation.
 *          1.17 2004/07/16 02:48:53  carliles Hist EQ doesn't look quite right, but there's nothing to compare it to,
 *                                             and the math looks right.
 *          1.16 2004/07/14 02:40:49  carliles Scaling should be done once and for all, with all possible accelerations.
 *                                             Now just have to add hist eq and asinh.
 *          1.15 2004/07/09 02:22:31  carliles Added log/sqrt maps, fixed wrong output for byte images (again).
 *          1.14 2004/06/21 05:38:39  carliles Got rescale lookup acceleration working for short images.
 *                                             Also in theory for int images, though I can't test because of
 *                                             dynamic range of my int image.
 *          1.13 2004/06/19 01:11:49  carliles Converted FITSImage to extend BufferedImage.
 *          1.12 2004/06/17 01:05:05  carliles Fixed some image orientation shit.
 *                                             Added getOriginalValue method to FITSImage.
 *          1.11 2004/06/16 22:27:20  carliles Fixed bug with ImageHDU crap in FITSImage.
 *          1.10 2004/06/16 22:21:02  carliles Added method to fetch ImageHDU from FITSImage.
 *          1.9  2004/06/07 21:14:06  carliles Rescale works nicely for all types now.
 *          1.8  2004/06/07 20:05:19  carliles Added rescale to FITSImage.
 *          1.7  2004/06/04 23:11:52  carliles Cleaned up histogram crap a bit.
 *          1.6  2004/06/04 01:01:36  carliles Got rid of some overmodelling.
 *          1.5  2004/06/02 22:17:37  carliles Got the hang of cut levels.
 *                                             Need to implement widely and as efficiently as possible.
 *          1.4  2004/06/02 19:39:36  carliles Adding histogram crap.
 *          1.3  2004/05/27 17:01:03  carliles ImageIO FITS reading "works".  Some cleanup would be good.
 *          1.2  2004/05/26 23:00:15  carliles Fucking Sun and their fucking BufferedImages everywhere.
 *          1.1  2004/05/26 16:56:11  carliles Initial checkin of separate FITS package.
 *          1.12 2003/08/19 19:12:30  carliles
 */
public class SlowFITSImage extends FITSImage {

    public SlowFITSImage(Fits fits)
            throws FitsException, DataTypeNotSupportedException,
            NoImageDataFoundException, IOException {
        this(fits, SCALE_LINEAR);
    }

    public SlowFITSImage(Fits fits, int scaleMethod)
            throws FitsException, DataTypeNotSupportedException,
            NoImageDataFoundException, IOException {
        this(fits, createScaledImage(fits, scaleMethod), scaleMethod);
    }

    public SlowFITSImage(Fits fits, BufferedImage delegate, int scaleMethod)
            throws FitsException, DataTypeNotSupportedException,
            NoImageDataFoundException, IOException {
        super(delegate.getColorModel(),
                delegate.getRaster().createCompatibleWritableRaster(),
                true, null);

        setFits(fits);
        setHistogram((Histogram) delegate.getProperty("histogram"));
        setDelegate(delegate);
        this.scaleMethod = scaleMethod;
        scaledData = (short[]) delegate.getProperty("scaledData");

        ImageHDU imageHDU = (ImageHDU) delegate.getProperty("imageHDU");
        if (imageHDU == null) {
            imageHDU = findFirstImageHDU(fits);
        }
        setImageHDU(imageHDU);

        min = getHistogram().getMin();
        max = getHistogram().getMax();
        sigma = getHistogram().estimateSigma();
    }

    public SlowFITSImage(File file)
            throws FitsException, DataTypeNotSupportedException,
            NoImageDataFoundException, IOException {
        this(new Fits(file));
    }

    public SlowFITSImage(File file, int scaleMethod)
            throws FitsException, DataTypeNotSupportedException,
            NoImageDataFoundException, IOException {
        this(new Fits(file), scaleMethod);
    }

    public SlowFITSImage(String filename)
            throws FitsException, DataTypeNotSupportedException,
            NoImageDataFoundException, IOException {
        this(new Fits(filename));
    }

    public SlowFITSImage(String filename, int scaleMethod)
            throws FitsException, DataTypeNotSupportedException,
            NoImageDataFoundException, IOException {
        this(new Fits(filename), scaleMethod);
    }

    public SlowFITSImage(URL url)
            throws FitsException, DataTypeNotSupportedException,
            NoImageDataFoundException, IOException {
        this(new Fits(url));
    }

    public SlowFITSImage(URL url, int scaleMethod)
            throws FitsException, DataTypeNotSupportedException,
            NoImageDataFoundException, IOException {
        this(new Fits(url), scaleMethod);
    }

    public static String[] getScaleNames() {
        return ScaleUtils.getScaleNames();
    }

    @Override
    public Fits getFits() {
        return fits;
    }

    @Override
    public ImageHDU getImageHDU() {
        return imageHDU;
    }

    protected void setHistogram(Histogram histogram) {
        this.histogram = histogram;
    }

    @Override
    public Histogram getHistogram() {
        return histogram;
    }

    @Override
    public double getOriginalValue(int x, int y) throws FitsException {
        double result = Double.NaN;
        double bZero = getImageHDU().getBZero();
        double bScale = getImageHDU().getBScale();
        Object data = getImageHDU().getData().getData();

        switch (getImageHDU().getBitpix()) {
        case BYTE:
            int dataVal = ((byte[][]) data)[y][x];
            if (dataVal < 0) {
                dataVal += 256;
            }
            result = bZero + bScale * dataVal;
            break;
        case SHORT:
            result = bZero + bScale * ((double) ((short[][]) data)[y][x]);
            break;
        case INTEGER:
            result = bZero + bScale * ((double) ((int[][]) data)[y][x]);
            break;
        case FLOAT:
            result = bZero + bScale * ((double) ((float[][]) data)[y][x]);
            break;
        case DOUBLE:
            result = bZero + bScale * ((double[][]) data)[y][x];
            break;
        default:
            break;
        }

        return result;
    }

    @Override
    public int getScaleMethod() {
        return scaleMethod;
    }

    @Override
    public void setScaleMethod(int scaleMethod) {
        if (scaleMethod != this.scaleMethod) {
            try {
                setDelegate(createScaledImage(getImageHDU(), scaledData,
                        getHistogram(), min, max,
                        sigma, scaleMethod));
                this.scaleMethod = scaleMethod;
            } catch (Exception e) {
                throw new RuntimeException(e);
            }
        }
    }

    @Override
    public void rescale(double min, double max, double sigma) {
        if (min != this.min || max != this.max || sigma != this.sigma) {
            try {
                this.min = min;
                this.max = max;
                this.sigma = sigma;
                setDelegate(createScaledImage(getImageHDU(), scaledData,
                        getHistogram(), min, max,
                        sigma, scaleMethod));
            } catch (Exception e) {
                throw new RuntimeException(e);
            }
        }
    }

    @Override
    protected BufferedImage getDelegate() {
        return delegate;
    }

    protected void setDelegate(BufferedImage delegate) {
        this.delegate = delegate;
    }

//#region BufferedImage METHODS

    @Override
    public WritableRaster copyData(WritableRaster outRaster) {
        return getDelegate().copyData(outRaster);
    }

    @Override
    public Graphics2D createGraphics() {
        throw new RuntimeException(new Exception().getStackTrace()[0].
                getMethodName() + " not supported");
    }

    @Override
    public void flush() {
        throw new RuntimeException(new Exception().getStackTrace()[0].
                getMethodName() + " not supported");
    }

    @Override
    public WritableRaster getAlphaRaster() {
        throw new RuntimeException(new Exception().getStackTrace()[0].
                getMethodName() + " not supported");
    }

    @Override
    public ColorModel getColorModel() {
        return getDelegate().getColorModel();
    }

    @Override
    public Raster getData() {
        return getDelegate().getData();
    }

    @Override
    public Raster getData(Rectangle rect) {
        return getDelegate().getData(rect);
    }

    @Override
    public Graphics getGraphics() {
        throw new RuntimeException(new Exception().getStackTrace()[0].
                getMethodName() + " not supported");
    }

    @Override
    public int getHeight() {
        return getDelegate().getHeight();
    }

    @Override
    public int getHeight(ImageObserver observer) {
        return getDelegate().getHeight(observer);
    }

    @Override
    public int getMinTileX() {
        return getDelegate().getMinTileX();
    }

    @Override
    public int getMinTileY() {
        return getDelegate().getMinTileY();
    }

    @Override
    public int getMinX() {
        return getDelegate().getMinX();
    }

    @Override
    public int getMinY() {
        return getDelegate().getMinY();
    }

    @Override
    public int getNumXTiles() {
        return getDelegate().getNumXTiles();
    }

    @Override
    public int getNumYTiles() {
        return getDelegate().getNumYTiles();
    }

    @Override
    public Object getProperty(String name) {
        return getDelegate().getProperty(name);
    }

    @Override
    public Object getProperty(String name, ImageObserver observer) {
        return getDelegate().getProperty(name, observer);
    }

    @Override
    public String[] getPropertyNames() {
        return getDelegate().getPropertyNames();
    }

    @Override
    public WritableRaster getRaster() {
        return getDelegate().getRaster();
    }

    @Override
    public int getRGB(int x, int y) {
        return getDelegate().getRGB(x, y);
    }

    @Override
    public int[] getRGB(int startX, int startY, int w, int h,
                        int[] rgbArray, int offset, int scansize) {
        return getDelegate().getRGB(startX, startY, w, h,
                rgbArray, offset, scansize);
    }

    @Override
    public SampleModel getSampleModel() {
        return getDelegate().getSampleModel();
    }

    @Override
    public ImageProducer getSource() {
        return getDelegate().getSource();
    }

    @Override
    public Vector<RenderedImage> getSources() {
        return getDelegate().getSources();
    }

    @Override
    public BufferedImage getSubimage(int x, int y, int w, int h) {
        return getDelegate().getSubimage(x, y, w, h);
    }

    @Override
    public Raster getTile(int tileX, int tileY) {
        return getDelegate().getTile(tileX, tileY);
    }

    @Override
    public int getTileGridXOffset() {
        return getDelegate().getTileGridXOffset();
    }

    @Override
    public int getTileGridYOffset() {
        return getDelegate().getTileGridYOffset();
    }

    @Override
    public int getTileHeight() {
        return getDelegate().getTileHeight();
    }

    @Override
    public int getTileWidth() {
        return getDelegate().getTileWidth();
    }

    @Override
    public int getType() {
        return getDelegate().getType();
    }

    @Override
    public int getWidth() {
        return getDelegate().getWidth();
    }

    @Override
    public int getWidth(ImageObserver observer) {
        return getDelegate().getWidth(observer);
    }

    @Override
    public WritableRaster getWritableTile(int tileX, int tileY) {
        throw new RuntimeException(new Exception().getStackTrace()[0].
                getMethodName() + " not supported");
    }

    @Override
    public Point[] getWritableTileIndices() {
        throw new RuntimeException(new Exception().getStackTrace()[0].
                getMethodName() + " not supported");
    }

    @Override
    public boolean hasTileWriters() {
        return false;
    }

    @Override
    public boolean isAlphaPremultiplied() {
        return true;
    }

    @Override
    public boolean isTileWritable(int tileX, int tileY) {
        return false;
    }

    @Override
    public void releaseWritableTile(int tileX, int tileY) {
        throw new RuntimeException(new Exception().getStackTrace()[0].
                getMethodName() + " not supported");
    }

    @Override
    public void setData(Raster r) {
        throw new RuntimeException(new Exception().getStackTrace()[0].
                getMethodName() + " not supported");
    }

    @Override
    public void setRGB(int x, int y, int rgb) {
        throw new RuntimeException(new Exception().getStackTrace()[0].
                getMethodName() + " not supported");
    }

    @Override
    public void setRGB(int startX, int startY, int w, int h,
                       int[] rgbArray, int offset, int scansize) {
        throw new RuntimeException(new Exception().getStackTrace()[0].
                getMethodName() + " not supported");
    }

    public String toString() {
        return getDelegate().toString();
    }

//#endregion BufferedImage METHODS

    @Override
    protected void setFits(Fits fits) {
        this.fits = fits;
    }

    @Override
    protected void setImageHDU(ImageHDU imageHDU) {
        this.imageHDU = imageHDU;
    }

    protected static ImageHDU findFirstImageHDU(Fits fits)
            throws FitsException, IOException {
        ImageHDU result = null;
        int i = 0;

        for (BasicHDU<?> hdu = fits.getHDU(i); hdu != null && result == null; ++i) {
            if (hdu instanceof ImageHDU) {
                result = (ImageHDU) hdu;
            }
        }

        return result;
    }

    protected static BufferedImage createScaledImage(Fits fits, int scaleMethod)
            throws FitsException, DataTypeNotSupportedException,
            NoImageDataFoundException, IOException {
        BufferedImage result;
        ImageHDU imageHDU = findFirstImageHDU(fits);

        if (imageHDU != null) {
            result = createScaledImage(imageHDU, scaleMethod);
        } else {
            throw new NoImageDataFoundException();
        }

        return result;
    }

    /**
     * @return An array of BufferedImages from hdu with intensity values scaled
     * to short range using linear, log, square root, and square scales,
     * in that order.
     */
    public static BufferedImage createScaledImage(ImageHDU hdu, int scaleMethod)
            throws FitsException, DataTypeNotSupportedException {
        Bitpix bitpix = hdu.getBitpix();
        int width = hdu.getAxes()[1]; // yes, the axes are in the wrong order
        int height = hdu.getAxes()[0];
        double bZero = hdu.getBZero();
        double bScale = hdu.getBScale();
        Object data = hdu.getData().getData();
        Histogram hist = switch (bitpix) {
            case BYTE -> ScaleUtils.computeHistogram((byte[][]) data, bZero, bScale);
            case SHORT -> ScaleUtils.computeHistogram((short[][]) data, bZero, bScale);
            case INTEGER -> ScaleUtils.computeHistogram((int[][]) data, bZero, bScale);
            case FLOAT -> {
Debug.println(StringUtil.paramString(data));
                yield ScaleUtils.computeHistogram(((float[][][]) data)[0], bZero, bScale); // TODO
            }
            case DOUBLE -> ScaleUtils.computeHistogram((double[][]) data, bZero, bScale);
            default -> throw new DataTypeNotSupportedException(bitpix);
        };

        return createScaledImage(hdu, null, hist, hist.getMin(), hist.getMax(),
                hist.estimateSigma(), scaleMethod);
    }

    public static BufferedImage createScaledImage(ImageHDU hdu,
                                                  short[] result,
                                                  Histogram hist,
                                                  double min, double max,
                                                  double sigma,
                                                  int scaleMethod)
            throws FitsException, DataTypeNotSupportedException {
        Bitpix bitpix = hdu.getBitpix();
        Object data = hdu.getData().getData();
        int width = hdu.getAxes()[1]; // yes, the axes are in the wrong order
        int height = hdu.getAxes()[0];
        double bZero = hdu.getBZero();
        double bScale = hdu.getBScale();
        short[] scaledData = SlowScaleUtils.scale(data, result, width, height,
                bZero, bScale, min, max,
                sigma, hist, scaleMethod);

        ColorModel cm =
                new ComponentColorModel(ColorSpace.getInstance(ColorSpace.CS_sRGB),
                        false, false, Transparency.OPAQUE,
                        DataBuffer.TYPE_USHORT);
        SampleModel sm = cm.createCompatibleSampleModel(width, height);

        Hashtable<String, Object> properties = new Hashtable<>();
        properties.put("histogram", hist);
        properties.put("imageHDU", hdu);
        properties.put("scaledData", scaledData);

        DataBuffer db = new DataBufferUShort(scaledData, height);
        WritableRaster r = Raster.createWritableRaster(sm, db, null);

        return new BufferedImage(cm, r, false, properties);
    }

    public static class DataTypeNotSupportedException extends Exception {
        public DataTypeNotSupportedException(Bitpix bitpix) {
            super(bitpix + " is not a valid FITS data type.");
        }
    }

    public static class NoImageDataFoundException extends Exception {
        public NoImageDataFoundException() {
            super("No image data found in FITS file.");
        }
    }

    protected Fits fits;
    protected ImageHDU imageHDU;

    protected int scaleMethod;
    protected final short[] scaledData;
    protected double sigma;
    protected double min;
    protected double max;
    protected Histogram histogram;
    protected BufferedImage delegate;
}
