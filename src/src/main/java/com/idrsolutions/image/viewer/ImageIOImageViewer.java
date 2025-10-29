/*
 * Copyright (c) 1997-2025 IDRsolutions (https://www.idrsolutions.com)
 */

package com.idrsolutions.image.viewer;

import com.idrsolutions.image.JDeli;
import com.idrsolutions.image.encoder.OutputFormat;
import org.jpedal.utils.LogWriter;

import javax.imageio.ImageIO;
import javax.imageio.ImageReader;
import javax.imageio.stream.ImageInputStream;
import javax.swing.JFileChooser;
import javax.swing.JOptionPane;
import javax.swing.filechooser.FileNameExtensionFilter;
import java.awt.Rectangle;
import java.awt.image.BufferedImage;
import java.io.File;
import java.io.IOException;
import java.util.Arrays;
import java.util.Iterator;

final class ImageIOImageViewer extends JavaImageViewer {

    private ImageIOImageViewer() {
        super("ImageIO Viewer");
    }

    public static void main(final String[] args) throws Exception {
        final ImageIOImageViewer viewer = new ImageIOImageViewer();
        viewer.run();
    }

    @Override
    void run() throws Exception {
        super.run();
        setJMenuBar(toolBar);
        setVisible(true);
    }

    @Override
    BufferedImage getImage(final ImageTab tab) {
        try {
            return ImageIO.read(tab.getFile());
        } catch (final IOException e) {
            LogWriter.error(e, "Exception thrown whilst loading image file");
        }
        return null;
    }

    @Override
    Rectangle getImageDimension(final ImageTab tab) {
        try {
            final ImageInputStream iis = ImageIO.createImageInputStream(tab.getFile());
            final Iterator<ImageReader> readers = ImageIO.getImageReaders(iis);

            if (readers.hasNext()) {
                final ImageReader reader = readers.next();
                reader.setInput(iis, true);

                return new Rectangle(reader.getWidth(0), reader.getHeight(0));
            }

        } catch (final IOException e) {
            LogWriter.writeLog("Unable to get image dimensions: " + e);
        }
        return new Rectangle(0, 0);
    }

    @Override
    String getImageType(final ImageTab tab) {
        try {
            final ImageInputStream iis = ImageIO.createImageInputStream(tab.getFile());
            final Iterator<ImageReader> readers = ImageIO.getImageReaders(iis);

            if (readers.hasNext()) {
                final ImageReader reader = readers.next();
                reader.setInput(iis, true);
                reader.getImageMetadata(0);
                return reader.getFormatName();
            }

        } catch (final IOException e) {
            LogWriter.writeLog("Unable to get image type: " + e);
        }
        return null;
    }

    @Override
    protected boolean isImageFormatSupported(final String format) {
        final String[] supportedFormats = ImageIO.getReaderFormatNames();
        for (final String imageFormat : supportedFormats) {
            if (imageFormat.equals(format)) {
                return true;
            }
        }
        return false;
    }

    @Override
    @SuppressWarnings("PMD.UnnecessaryCaseChange")
    void saveFile(final ImageTab tab) {
        final BufferedImage image = getImage(tab);
        if (image != null) {
            final JFileChooser fileChooser = new JFileChooser();
            Arrays.stream(ImageIO.getWriterFormatNames()).forEach(a -> {
                if (!a.equals(a.toLowerCase())) {
                    fileChooser.addChoosableFileFilter(new FileNameExtensionFilter(a, a));
                }
            });
            fileChooser.setFileHidingEnabled(true);
            fileChooser.setAcceptAllFileFilterUsed(false);
            fileChooser.showSaveDialog(this);
            try {
                if (fileChooser.getSelectedFile() != null) {
                    final String format = fileChooser.getFileFilter().getDescription();
                    ImageIO.write(image, format, new File(fileChooser.getSelectedFile().getAbsolutePath() + '.' + format));
                    JOptionPane.showMessageDialog(this, "File saved");
                }
            } catch (final Exception e) {
                JOptionPane.showMessageDialog(this, "Cannot save file");

            }
        } else {
            JOptionPane.showMessageDialog(this, "Cannot save file");
        }
    }

    @Override
    void saveFiles() {
        final BufferedImage image = getImage(tabs.get(imageTabs.getSelectedIndex()));
        if (image != null) {
            final JFileChooser fileChooser = new JFileChooser();
            fileChooser.setFileSelectionMode(JFileChooser.DIRECTORIES_ONLY);
            Arrays.stream(OutputFormat.values()).forEach(x -> fileChooser.addChoosableFileFilter(new FileNameExtensionFilter(x.name(), x.name())));
            fileChooser.setFileHidingEnabled(true);
            fileChooser.setAcceptAllFileFilterUsed(false);
            fileChooser.showSaveDialog(this);
            final File folder = fileChooser.getSelectedFile();
            if (!folder.getName().isEmpty() && !folder.exists()) {
                folder.mkdir();
            }
            try {
                for (final ImageTab tab : tabs) {
                    draw(tab);
                    if (fileChooser.getSelectedFile() != null) {
                        final String format = fileChooser.getFileFilter().getDescription();
                        final String name = tab.getFile().getName();
                        JDeli.write(image, format, new File(fileChooser.getSelectedFile() + File.separator + name.substring(0, name.indexOf('.')) + '.' + format));
                    }
                }
                JOptionPane.showMessageDialog(this, "Files saved");
            } catch (final Exception e) {
                JOptionPane.showMessageDialog(this, "Cannot save files");

            }
        }
    }
}
