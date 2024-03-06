/*
 * ID:            $Id: FITSImage.java,v 1.21 2004/07/23 21:54:11 carliles Exp $
 * Revision:      $Revision: 1.21 $
 * Date/time:     $Date: 2004/07/23 21:54:11 $
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


/**
 * FITSImage.
 * <p>
 * The scaling algorithms offered are linear, log, square root, square,
 * histogram equalization, and inverse hyperbolic sine.  Note that the histogram
 * equalization algorithm is just that; it works to fit the values to a uniform
 * distribution curve.  The inverse hyperbolic sine scaling has linear behavior
 * below the sigma parameter and logarithmic behavior above the sigma parameter.
 *
 * @author carliles
 * @version  1.21 2004/07/23 21:54:11  carliles Added javadocs.
 *           1.20 2004/07/22 22:29:08  carliles Added "low" memory consumption SlowFITSImage.
 *           1.19 2004/07/21 22:24:57  carliles Removed some commented crap.
 *           1.18 2004/07/21 18:03:55  carliles Added asinh with sigma estimation.
 *           1.17 2004/07/16 02:48:53  carliles Hist EQ doesn't look quite right, but there's nothing
 *                                               to compare it to, and the math looks right.
 *           1.16 2004/07/14 02:40:49  carliles Scaling should be done once and for all, with all possible
 *                                               accelerations. Now just have to add hist eq and asinh.
 *           1.15 2004/07/09 02:22:31  carliles Added log/sqrt maps, fixed wrong output for byte images (again).
 *           1.14 2004/06/21 05:38:39  carliles Got rescale lookup acceleration working for short images.
 *                                               Also in theory for int images, though I can't test because of
 *                                               dynamic range of my int image.
 *           1.13 2004/06/19 01:11:49  carliles Converted FITSImage to extend BufferedImage.
 *           1.12 2004/06/17 01:05:05  carliles Fixed some image orientation shit.
 *                                               Added getOriginalValue method to FITSImage.
 *           1.11 2004/06/16 22:27:20  carliles Fixed bug with ImageHDU crap in FITSImage.
 *           1.10 2004/06/16 22:21:02  carliles Added method to fetch ImageHDU from FITSImage.
 *           1.9  2004/06/07 21:14:06  carliles Rescale works nicely for all types now.
 *           1.8  2004/06/07 20:05:19  carliles Added rescale to FITSImage.
 *           1.7  2004/06/04 23:11:52  carliles Cleaned up histogram crap a bit.
 *           1.6  2004/06/04 01:01:36  carliles Got rid of some overmodelling.
 *           1.5  2004/06/02 22:17:37  carliles Got the hang of cut levels.
 *                                              Need to implement widely and as efficiently as possible.
 *           1.4  2004/06/02 19:39:36  carliles Adding histogram crap.
 *           1.3  2004/05/27 17:01:03  carliles ImageIO FITS reading "works".  Some cleanup would be good.
 *           1.2  2004/05/26 23:00:15  carliles Fucking Sun and their fucking BufferedImages everywhere.
 *           1.1  2004/05/26 16:56:11  carliles Initial checkin of separate FITS package.
 *           1.12 2003/08/19 19:12:30  carliles
 */
public class FITSImage extends BufferedImage {

    public static final int SCALE_LINEAR = ScaleUtils.LINEAR;
    public static final int SCALE_LOG = ScaleUtils.LOG;
    public static final int SCALE_SQUARE_ROOT = ScaleUtils.SQUARE_ROOT;
    public static final int SCALE_SQUARE = ScaleUtils.SQUARE;
    public static final int SCALE_HISTOGRAM_EQUALIZATION = ScaleUtils.HIST_EQ;
    public static final int SCALE_ASINH = ScaleUtils.ASINH;

    public FITSImage(Fits fits)
            throws FitsException, DataTypeNotSupportedException,
            NoImageDataFoundException, IOException {
        this(fits, SCALE_LINEAR);
    }

    public FITSImage(Fits fits, int scaleMethod)
            throws FitsException, DataTypeNotSupportedException,
            NoImageDataFoundException, IOException {
        this(fits, createScaledImages(fits), scaleMethod);
    }

    public FITSImage(Fits fits, BufferedImage[] scaledImages, int scaleMethod)
            throws FitsException, DataTypeNotSupportedException,
            NoImageDataFoundException, IOException {
        super(scaledImages[0].getColorModel(),
                scaledImages[0].getRaster().createCompatibleWritableRaster(),
                true, null);

        setFits(fits);
        setScaledImages(scaledImages);
        setScaleMethod(scaleMethod);

        ImageHDU imageHDU = (ImageHDU) scaledImages[0].getProperty("imageHDU");
        if (imageHDU == null) {
            imageHDU = findFirstImageHDU(fits);
        }
        setImageHDU(imageHDU);

        _min = getHistogram().getMin();
        _max = getHistogram().getMax();
    }

    public FITSImage(File file)
            throws FitsException, DataTypeNotSupportedException,
            NoImageDataFoundException, IOException {
        this(new Fits(file));
    }

    public FITSImage(File file, int scaleMethod)
            throws FitsException, DataTypeNotSupportedException,
            NoImageDataFoundException, IOException {
        this(new Fits(file), scaleMethod);
    }

    public FITSImage(String filename)
            throws FitsException, DataTypeNotSupportedException,
            NoImageDataFoundException, IOException {
        this(new Fits(filename));
    }

    public FITSImage(String filename, int scaleMethod)
            throws FitsException, DataTypeNotSupportedException,
            NoImageDataFoundException, IOException {
        this(new Fits(filename), scaleMethod);
    }

    public FITSImage(URL url)
            throws FitsException, DataTypeNotSupportedException,
            NoImageDataFoundException, IOException {
        this(new Fits(url));
    }

    public FITSImage(URL url, int scaleMethod)
            throws FitsException, DataTypeNotSupportedException,
            NoImageDataFoundException, IOException {
        this(new Fits(url), scaleMethod);
    }

    /**
     * @return Printable names of the different scaling algorithms, indexed as
     * <CODE>SCALE_LINEAR</CODE>, <CODE>SCALE_LOG</CODE>, etc.
     */
    public static String[] getScaleNames() {
        return ScaleUtils.getScaleNames();
    }

    public Fits getFits() {
        return _fits;
    }

    public ImageHDU getImageHDU() {
        return _imageHDU;
    }

    public Histogram getHistogram() {
        return (Histogram) getScaledImages()[SCALE_LINEAR].getProperty("histogram");
    }

    /**
     * @return The actual data value at position (x, y), with bZero and bScale
     * applied.
     */
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

    /**
     * @return One of <CODE>SCALE_LINEAR</CODE>, <CODE>SCALE_LOG</CODE>, etc.
     */
    public int getScaleMethod() {
        return _scaleMethod;
    }

    /**
     * <CODE>scaleMethod</CODE> must be one of
     * <CODE>SCALE_LINEAR</CODE>, <CODE>SCALE_LOG</CODE>, etc.  The image must be
     * redrawn after this method is called for the change to be visible.
     */
    public void setScaleMethod(int scaleMethod) {
        _scaleMethod = scaleMethod;
    }

    /**
     * Rescales the image with the given min and max range values.
     * <CODE>sigma</CODE> is used for the inverse hyperbolic sine scaling as the
     * value (in the range of the data values with bZero and bScale applied)
     * at which the behavior becomes more logarithmic and less linear.  The image
     * must be redrawn after this method is called for the change to be visible.
     */
    public void rescale(double min, double max, double sigma)
            throws FitsException, DataTypeNotSupportedException,
            NoImageDataFoundException, IOException {
        if (min != _min || max != _max || sigma != _sigma) {
            _min = min;
            _max = max;
            _sigma = sigma;
            setScaledImages(createScaledImages(getImageHDU(), getHistogram(),
                    min, max, sigma));
            setScaleMethod(getScaleMethod());
        }
    }

    protected BufferedImage getDelegate() {
        return getScaledImages()[getScaleMethod()];
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

    protected FITSImage(ColorModel cm, WritableRaster r,
                        boolean isRasterPremultiplied, Hashtable properties) {
        super(cm, r, isRasterPremultiplied, properties);
    }

    protected void setFits(Fits fits) {
        _fits = fits;
    }

    protected void setImageHDU(ImageHDU imageHDU) {
        _imageHDU = imageHDU;
    }

    protected void setScaledImages(BufferedImage[] scaledImages) {
        _scaledImages = scaledImages;
    }

    protected BufferedImage[] getScaledImages() {
        return _scaledImages;
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

    protected static BufferedImage[] createScaledImages(Fits fits)
            throws FitsException, DataTypeNotSupportedException,
            NoImageDataFoundException, IOException {
        BufferedImage[] result = null;
        ImageHDU imageHDU = findFirstImageHDU(fits);

        if (imageHDU != null) {
            result = createScaledImages(imageHDU);
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
    public static BufferedImage[] createScaledImages(ImageHDU hdu)
            throws FitsException, DataTypeNotSupportedException {
        Bitpix bitpix = hdu.getBitpix();
        double bZero = hdu.getBZero();
        double bScale = hdu.getBScale();
        Object data = hdu.getData().getData();
        Histogram hist = switch (bitpix) {
            case BYTE -> ScaleUtils.computeHistogram((byte[][]) data, bZero, bScale);
            case SHORT -> ScaleUtils.computeHistogram((short[][]) data, bZero, bScale);
            case INTEGER -> ScaleUtils.computeHistogram((int[][]) data, bZero, bScale);
            case FLOAT -> ScaleUtils.computeHistogram((float[][]) data, bZero, bScale);
            case DOUBLE -> ScaleUtils.computeHistogram((double[][]) data, bZero, bScale);
            default -> throw new DataTypeNotSupportedException(bitpix);
        };

        return createScaledImages(hdu, hist,
                hist.getMin(), hist.getMax(),
                hist.estimateSigma());
    }

    public static BufferedImage[] createScaledImages(ImageHDU hdu, Histogram hist,
                                                     double min, double max,
                                                     double sigma)
            throws FitsException, DataTypeNotSupportedException {
        Bitpix bitpix = hdu.getBitpix();
        int width = hdu.getAxes()[1]; // yes, the axes are in the wrong order
        int height = hdu.getAxes()[0];
        double bZero = hdu.getBZero();
        double bScale = hdu.getBScale();
        Object data = hdu.getData().getData();
        short[][] scaledData = switch (bitpix) {
            case BYTE -> ScaleUtils.scaleToUShort((byte[][]) data, hist,
                    width, height, bZero, bScale,
                    min, max, sigma);
            case SHORT -> ScaleUtils.scaleToUShort((short[][]) data, hist,
                    width, height, bZero, bScale,
                    min, max, sigma);
            case INTEGER -> ScaleUtils.scaleToUShort((int[][]) data, hist,
                    width, height, bZero, bScale,
                    min, max, sigma);
            case FLOAT -> ScaleUtils.scaleToUShort((float[][]) data, hist,
                    width, height, bZero, bScale,
                    min, max, sigma);
            case DOUBLE -> ScaleUtils.scaleToUShort((double[][]) data, hist,
                    width, height, bZero, bScale,
                    min, max, sigma);
            default -> throw new DataTypeNotSupportedException(bitpix);
        };

        ColorModel cm =
                new ComponentColorModel(ColorSpace.getInstance(ColorSpace.CS_sRGB),
                        false, false, Transparency.OPAQUE,
                        DataBuffer.TYPE_USHORT);
        SampleModel sm = cm.createCompatibleSampleModel(width, height);

        Hashtable<String, Object> properties = new Hashtable<>();
        properties.put("histogram", hist);
        properties.put("imageHDU", hdu);

        BufferedImage[] result = new BufferedImage[scaledData.length];

        for (int i = 0; i < result.length; ++i) {
            DataBuffer db = new DataBufferUShort(scaledData[i], height);
            WritableRaster r = Raster.createWritableRaster(sm, db, null);

            result[i] = new BufferedImage(cm, r, false, properties);
        }

        return result;
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

    protected Fits _fits;
    protected ImageHDU _imageHDU;
    protected BasicHDU<?>[] _hdus;

    protected int _scaleMethod;
    protected double _sigma = Double.NaN;
    protected double _min = Double.NaN;
    protected double _max = Double.NaN;
    protected BufferedImage[] _scaledImages;
}
