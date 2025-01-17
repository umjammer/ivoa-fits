package edu.jhu.pha.sdss.fits.imageio;

import java.io.IOException;
import java.io.InputStream;
import java.io.PushbackInputStream;
import java.util.zip.GZIPInputStream;
import javax.imageio.stream.ImageInputStream;


public class ImageInputStreamInputStream extends PushbackInputStream {

    public ImageInputStreamInputStream(ImageInputStream in) throws IOException {
        super(gunzipIfNecessary(new PushbackInputStream(new ImageInputStreamWrap(in),
                2)), 100);
    }

    protected static InputStream gunzipIfNecessary(PushbackInputStream in)
            throws IOException {
        InputStream result = in;

        // check for gzip stream
        byte[] buf = new byte[2];
        result.read(buf, 0, 2);
        ((PushbackInputStream) result).unread(buf);

        // see http://www.ietf.org/rfc/rfc1952.txt
        if (buf[0] == (byte) 31 && buf[1] == (byte) 139) {
            result = new GZIPInputStream(result);
        }

        return result;
    }

    protected static class ImageInputStreamWrap extends InputStream {
        public ImageInputStreamWrap(ImageInputStream in) {
            _in = in;
        }

        @Override
        public int available() throws IOException {
            long avail = _in.length();

            if (avail == -1L) {
                return 0;
            }

            if (avail > (long) Integer.MAX_VALUE) {
                return Integer.MAX_VALUE;
            }

            return (int) avail;
        }

        @Override
        public void close() throws IOException {
            _in.close();
        }

        @Override
        public void mark(int readlimit) {
            _in.mark();
        }

        @Override
        public boolean markSupported() {
            return false;
        }

        @Override
        public int read() throws IOException {
            return _in.read();
        }

        @Override
        public int read(byte[] b) throws IOException {
            return _in.read(b);
        }

        @Override
        public int read(byte[] b, int off, int len) throws IOException {
            return _in.read(b, off, len);
        }

        @Override
        public void reset() throws IOException {
            _in.reset();
        }

        @Override
        public long skip(long n) throws IOException {
            return _in.skipBytes(n);
        }

        protected final ImageInputStream _in;
    }
}
