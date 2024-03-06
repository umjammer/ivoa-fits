/*
 * Written by Samuel Carliles 2004.
 *
 * ID:            $Id: FITSReader.java,v 1.8 2006/08/24 20:55:28 carliles Exp $
 * Revision:      $Revision: 1.8 $
 * Date/time:     $Date: 2006/08/24 20:55:28 $
 */

package edu.jhu.pha.sdss.fits.imageio;

import java.awt.image.BufferedImage;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import javax.imageio.ImageReadParam;
import javax.imageio.ImageReader;
import javax.imageio.ImageTypeSpecifier;
import javax.imageio.metadata.IIOMetadata;
import javax.imageio.spi.ImageReaderSpi;
import javax.imageio.stream.ImageInputStream;

import edu.jhu.pha.sdss.fits.FITSImage;
import edu.jhu.pha.sdss.fits.SlowFITSImage;
import nom.tam.fits.Fits;
import vavi.util.Debug;


/**
 * FITSReader.
 *
 * @author carliles
 * @version 1.8   2006/08/24 20:55:28  carliles Updated to work with change in jdk implementation.
 *          1.7   2004/07/22 22:29:09  carliles Added "low" memory consumption SlowFITSImage.
 *          1.6   2004/06/19 01:11:49  carliles Converted FITSImage to extend BufferedImage.
 *          1.5   2004/05/27 18:13:50  carliles Added more implementation to FITSReader, though none of it
 *                                             appears to be used, and none has been tested.
 *          1.4   2004/05/27 17:01:03  carliles ImageIO FITS reading "works".  Some cleanup would be good.
 *          1.3   2004/05/26 21:28:59  carliles FITSReaderSpi looks pretty done.
 *          1.2   2004/05/26 17:10:00  carliles Getting CVS crap in files in place.
 *          1.1   2004/05/26 16:56:11  carliles Initial checkin of separate FITS package.
 *          1.12  2003/08/19 19:12:30  carliles
 */
public class FITSReader extends ImageReader {

    public FITSReader(ImageReaderSpi originatingProvider, Object extensionObject) {
        super(originatingProvider);
        // what the hell is an extension object?  I don't think we need it.
    }

    @Override
    public IIOMetadata getImageMetadata(int imageIndex) {
        return null;
    }

    @Override
    public IIOMetadata getStreamMetadata() {
        return null;
    }

    @Override
    public Iterator<ImageTypeSpecifier> getImageTypes(int imageIndex) {
        // Returns an Iterator containing possible image types to which the
        // given image may be decoded, in the form of ImageTypeSpecifiers.
        read(imageIndex, null);

        return _imageTypeSpecifiers.iterator();
    }

    @Override
    public int getNumImages(boolean allowSearch) {
        return read(0, null) == null ? 0 : 1;
    }

    @Override
    public int getHeight(int imageIndex) {
        int result = 0;

        BufferedImage image = read(imageIndex, null);
        if (image != null) {
            result = image.getHeight();
        }

        return result;
    }

    @Override
    public int getWidth(int imageIndex) {
        int result = 0;

        BufferedImage image = read(imageIndex, null);
        if (image != null) {
            result = image.getWidth();
        }

        return result;
    }

    /**
     * We ignore both parameters, because we expect only one image per stream,
     * and we only read it one way.
     */
    @Override
    public BufferedImage read(int imageIndex, ImageReadParam param) {
        FITSImage result = getImage();

        if (result == null && !doneReading()) {
            try {
                InputStream in = new ImageInputStreamInputStream((ImageInputStream) getInput());

                result = new SlowFITSImage(new Fits(in));
            } catch (Exception e) {
                Debug.printStackTrace(e);
                result = null;
            }

            setImage(result);
            setDoneReading(true);
            _imageTypeSpecifiers.add(new ImageTypeSpecifier(getImage()));
        }

        return result;
    }

    /**
     * @return CVS Revision number.
     */
    public static String revision() {
        return "$Revision: 1.8 $";
    }

    protected FITSImage getImage() {
        return _image;
    }

    protected void setImage(FITSImage image) {
        _image = image;
    }

    protected boolean doneReading() {
        return _doneReading;
    }

    protected void setDoneReading(boolean done) {
        _doneReading = done;
    }

    protected FITSImage _image;
    protected boolean _doneReading = false;

    protected final List<ImageTypeSpecifier> _imageTypeSpecifiers = new ArrayList<>();
}
