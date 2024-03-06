/*
 * Written by Samuel Carliles 2004.
 *
 * ID:            $Id: FITSReaderSpi.java,v 1.4 2006/08/24 20:55:28 carliles Exp $
 * Revision:      $Revision: 1.4 $
 * Date/time:     $Date: 2006/08/24 20:55:28 $
 */

package edu.jhu.pha.sdss.fits.imageio;

import java.io.InputStream;
import java.util.Locale;
import java.util.Properties;
import javax.imageio.ImageReader;
import javax.imageio.spi.ImageReaderSpi;
import javax.imageio.stream.ImageInputStream;

import vavi.util.Debug;


/**
 * FITSReaderSpi.
 *
 * @author carliles
 * @version 1.4  2006/08/24 20:55:28  carliles to work with change in jdk implementation.
 *          1.3  2004/05/27 17:01:03  carliles ImageIO FITS reading "works".  Some cleanup would be good.
 *          1.2  2004/05/26 21:28:59  carliles FITSReaderSpi looks pretty done.
 *          1.1  2004/05/26 17:08:03  carliles Created imageio package.
 *          1.2  2004/05/26 16:59:44  carliles Fixed some package crap.
 *          1.1  2004/05/26 16:56:11  carliles Initial checkin of separate FITS package.
 *          1.6  2003/07/25 00:55:24  carliles
 */
public class FITSReaderSpi extends ImageReaderSpi {

    static {
        try {
            try (InputStream is = FITSReaderSpi.class.getResourceAsStream("/META-INF/maven/sdss/ivoa-fits/pom.properties")) {
                if (is != null) {
                    Properties props = new Properties();
                    props.load(is);
                    revision = props.getProperty("version", "undefined in pom.properties");
                } else {
                    revision = System.getProperty("vavi.test.version", "undefined");
                }
            }
        } catch (Exception e) {
            throw new IllegalStateException(e);
        }
    }

    /** */
    private static final String revision;

    public FITSReaderSpi() {
        super("JHU/NVO", // vendorName
                revision, // version
                new String[] {
                        "FITS", "fits"
                }, // names
                new String[] {
                        "fits", "fits.gz"
                }, // suffixes
                new String[] {
                        "image/x-fits", "image/x-gfits"
                }, // MIMETypes
                FITSReader.class.getName(), // readerClassName
                new Class[] {
                        ImageInputStream.class
                }, // inputTypes
                null, // writerSpiNames
                false, // supportsStandardStreamMetadataFormat
                null, // nativeStreamMetadataFormatName
                null, // nativeStreamMetadataFormatClassName
                null, // extraStreamMetadataFormatNames
                null, // extraStreamMetadataFormatClassNames
                false, // supportsStandardImageMetadataFormat
                null, // nativeImageMetadataFormatName
                null, // nativeImageMetadataFormatClassName
                null, // extraImageMetadataFormatNames
                null // extraImageMetadataFormatClassNames
        );
    }

    @Override
    public boolean canDecodeInput(Object source) {
        boolean result = source instanceof ImageInputStream;

        // Returns true if the supplied source object appears to be of the
        // format supported by this reader.
        if (result) {
            try {
                ImageInputStreamInputStream in =
                        new ImageInputStreamInputStream((ImageInputStream) source);

                // check for FITS
                byte[] buf = new byte[80];
                in.read(buf, 0, 80);
                in.unread(buf);

                result = new String(buf).replaceAll("[ ]+", " ").startsWith("SIMPLE = T");
            } catch (Exception e) {
                Debug.printStackTrace(e);
                result = false;
            }
        }

        return result;
    }

    @Override
    public ImageReader createReaderInstance() {
        return createReaderInstance(null);
    }

    @Override
    public ImageReader createReaderInstance(Object extension) {
        return new FITSReader(this, null);
    }

    @Override
    public String[] getImageWriterSpiNames() {
        return null;
    }

    @Override
    public Class<?>[] getInputTypes() {
        return inputTypes;
    }

    @Override
    public boolean isOwnReader(ImageReader reader) {
        return (reader instanceof FITSReader);
    }

    @Override
    public String getDescription(Locale locale) {
        return "It reads FITS images, including gzipped FITS images.";
    }
}
