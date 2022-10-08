package edu.jhu.pha.sdss.fits;

import java.awt.Dimension;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.geom.AffineTransform;
import java.awt.image.BufferedImage;
import java.awt.image.RenderedImage;
import java.nio.file.Files;
import java.nio.file.Paths;
import javax.imageio.ImageIO;
import javax.swing.JComponent;
import javax.swing.JFrame;


public class FITSTest {

    /**
     * @param args 0: fits
     */
    public static void main(String[] args) throws Exception {

        BufferedImage image = ImageIO.read(Files.newInputStream(Paths.get(args[0])));

        JFrame frame = new JFrame("FITS Test");

        JComponent imagePanel = createImagePanel(image);
        imagePanel.setPreferredSize(new Dimension(image.getWidth(), image.getHeight()));

        frame.getContentPane().add(imagePanel);
        frame.pack();
        frame.setVisible(true);

        if (image instanceof FITSImage) {
            FITSImage fimage = (FITSImage) image;

            try {
                Thread.sleep(1000);
                fimage.setScaleMethod(FITSImage.SCALE_LOG);
                frame.repaint();
                Thread.sleep(1000);
                fimage.setScaleMethod(FITSImage.SCALE_SQUARE_ROOT);
                frame.repaint();
                Thread.sleep(1000);
                fimage.setScaleMethod(FITSImage.SCALE_SQUARE);
                frame.repaint();
                Thread.sleep(1000);
                fimage.setScaleMethod(FITSImage.SCALE_ASINH);
                frame.repaint();
            } catch (RuntimeException e) {
                e.getCause().printStackTrace();
            }

            System.out.println("Finding max value");
            System.out.println("The easy way... max = " + fimage.getHistogram().getMax());
            System.out.println("The unreliable way... max = " + fimage.getImageHDU().getMaximumValue());

            // Now comes the hard way.
            double bScale = fimage.getImageHDU().getBScale();
            double bZero = fimage.getImageHDU().getBZero();
            double max = Double.MIN_VALUE;
            double val;
            Object data = fimage.getImageHDU().getData().getData();
            int[] axes = fimage.getImageHDU().getAxes();

            // this is the dirty little secret about nom.tam.fits.
            // it gives you a 2D array of arbitrary type, and the only thing
            // you can do is test for each type and write the same algorithm
            // 5 times!  But I don't think Tom had much choice because Java
            // doesn't give you any way to address these primitive types with a
            // single supertype.
            switch (fimage.getImageHDU().getBitPix()) {
            case 8:
                int shifted = 0;
                for (int x = 0; x < axes[1]; ++x) {
                    for (int y = 0; y < axes[0]; ++y) {
                        // have to shift byte values because FITS specifies unsigned
                        // and Java byte is signed.
                        shifted = ((byte[][]) data)[y][x];
                        shifted = shifted < 0 ? shifted + 256 : shifted;
                        val = bScale * shifted + bZero;
                        max = Math.max(val, max);
                    }
                }
                break;
            case 16:
                for (int x = 0; x < axes[1]; ++x) {
                    for (int y = 0; y < axes[0]; ++y) {
                        val = bScale * ((double) ((short[][]) data)[y][x]) + bZero;
                        max = Math.max(val, max);
                    }
                }
                break;
            case 32:
                for (int x = 0; x < axes[1]; ++x) {
                    for (int y = 0; y < axes[0]; ++y) {
                        val = bScale * ((double) ((int[][]) data)[y][x]) + bZero;
                        max = Math.max(val, max);
                    }
                }
                break;
            case -32:
                for (int x = 0; x < axes[1]; ++x) {
                    for (int y = 0; y < axes[0]; ++y) {
                        val = bScale * ((double) ((float[][]) data)[y][x]) + bZero;
                        max = Math.max(val, max);
                    }
                }
                break;
            case -64:
                for (int x = 0; x < axes[1]; ++x) {
                    for (int y = 0; y < axes[0]; ++y) {
                        val = bScale * ((double[][]) data)[y][x] + bZero;
                        max = Math.max(val, max);
                    }
                }
                break;
            default:
                break;
            }
            System.out.println("The hard way... max = " + max);
        }
    }

    protected static JComponent createImagePanel(final RenderedImage image) {

        return new JComponent() {
            public void paintComponent(Graphics g) {
                revalidate();

                if (image != null) {
                    ((Graphics2D) g).drawRenderedImage(image, new AffineTransform());
                }
            }
        };
    }
}
