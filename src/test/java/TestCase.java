/*
 * Copyright (c) 2022 by Naohide Sano, All rights reserved.
 *
 * Programmed by Naohide Sano
 */

import java.awt.Dimension;
import java.awt.Graphics;
import java.awt.event.WindowAdapter;
import java.awt.event.WindowEvent;
import java.awt.image.BufferedImage;
import java.io.InputStream;
import java.util.Arrays;
import java.util.concurrent.CountDownLatch;
import javax.imageio.ImageIO;
import javax.swing.JFrame;
import javax.swing.JPanel;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfSystemProperty;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;


/**
 * TestCase.
 *
 * @author <a href="mailto:umjammer@gmail.com">Naohide Sano</a> (nsano)
 * @version 0.00 2022-09-07 nsano initial version <br>
 */
class TestCase {

    /** using cdl cause junit stops awt thread suddenly */
    static void show(BufferedImage image) throws Exception {
        CountDownLatch cdl = new CountDownLatch(1);
        JFrame frame = new JFrame();
        frame.addWindowListener(new WindowAdapter() {
            @Override public void windowClosing(WindowEvent e) { cdl.countDown(); }
        });
        JPanel panel = new JPanel() {
            @Override
            public void paint(Graphics g) {
                g.drawImage(image, 0, 0, this);
            }
        };
        panel.setPreferredSize(new Dimension(image.getWidth(), image.getHeight()));
        frame.setContentPane(panel);
        frame.setTitle("FITS");
        frame.pack();
        frame.setVisible(true);
        cdl.await();
    }

    @Test
    void test00() throws Exception {
        String[] rs = ImageIO.getReaderFormatNames();
System.err.println("-- reader --");
for (String r : rs) {
    System.err.println(r);
}
        assertTrue(Arrays.asList(rs).contains("FITS"));
        String[] ws = ImageIO.getWriterFormatNames();
System.err.println("-- writer --");
for (String w : ws) {
    System.err.println(w);
}
        assertFalse(Arrays.asList(ws).contains("FITS"));
    }

    @Test
    @EnabledIfSystemProperty(named = "vavi.test", matches = "ide")
    void test2() throws Exception {
        String file = "/samples/HorseHead.fits";
        InputStream is = TestCase.class.getResourceAsStream(file);
        show(ImageIO.read(is));
    }
}