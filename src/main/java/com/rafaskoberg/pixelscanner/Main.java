package com.rafaskoberg.pixelscanner;

import javax.imageio.ImageIO;
import javax.swing.*;
import javax.swing.text.AttributeSet;
import javax.swing.text.BadLocationException;
import javax.swing.text.Style;
import javax.swing.text.StyleConstants;
import java.awt.*;
import java.awt.datatransfer.DataFlavor;
import java.awt.dnd.DnDConstants;
import java.awt.dnd.DropTarget;
import java.awt.dnd.DropTargetAdapter;
import java.awt.dnd.DropTargetDropEvent;
import java.awt.image.BufferedImage;
import java.io.File;
import java.io.IOException;
import java.util.HashMap;
import java.util.List;
import java.util.Objects;

public class Main {
    private static Style defaultStyle;
    private static Style commentStyle;
    private static Style stringStyle;
    private static Style pixelsStyle;
    private static Style highPercentageStyle;
    private static Style lowPercentageStyle;

    private static final float HIGH_PERCENTAGE_THRESHOLD = 0.2f;

    public static void main(String[] args) {
        SwingUtilities.invokeLater(() -> {

            // Creating the Frame
            JFrame frame = new JFrame("Area in Pixels Scanner");
            frame.setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
            frame.setSize(720, 810);

            // Center the frame on the screen
            Dimension screenSize = Toolkit.getDefaultToolkit().getScreenSize();
            int x = (screenSize.width - frame.getWidth()) / 2;
            int y = (screenSize.height - frame.getHeight()) / 2;
            frame.setLocation(x, y);

            // Wrap the JTextArea in a JScrollPane
            JTextPane textPane = new JTextPane();
            JScrollPane scrollPane = new JScrollPane(textPane);

            // Set monospaced font
            Font monospacedFont = new Font(Font.MONOSPACED, Font.BOLD, 16);
            textPane.setFont(monospacedFont);

            // Adding Components to the frame
            frame.getContentPane().add(BorderLayout.CENTER, scrollPane);
            frame.setVisible(true);

            // Create styles
            var doc = textPane.getStyledDocument();
            defaultStyle = doc.addStyle("default", null);
            StyleConstants.setFontFamily(defaultStyle, "Monospaced");
            commentStyle = doc.addStyle("comment", defaultStyle);
            StyleConstants.setForeground(commentStyle, Color.decode("#9c9a8b"));
            stringStyle = doc.addStyle("string", defaultStyle);
            StyleConstants.setForeground(stringStyle, Color.decode("#fd971f"));
            pixelsStyle = doc.addStyle("pixels", defaultStyle);
            StyleConstants.setForeground(pixelsStyle, Color.decode("#ae81ff"));
            highPercentageStyle = doc.addStyle("highPercentages", defaultStyle);
            StyleConstants.setForeground(highPercentageStyle, Color.decode("#f92672"));
            lowPercentageStyle = doc.addStyle("lowPercentages", defaultStyle);
            StyleConstants.setForeground(lowPercentageStyle, Color.decode("#66d9ef"));

            // Enable drag and drop
            setDropTargetRecursively(frame, new DropTargetAdapter() {
                @Override
                public void drop(DropTargetDropEvent event) {
                    try {
                        event.acceptDrop(DnDConstants.ACTION_COPY);
                        List<File> droppedFiles = (List<File>) event.getTransferable().getTransferData(DataFlavor.javaFileListFlavor);

                        // Set initial text first
                        textPane.setText("");
                        print(textPane, "Scanning directories:\n", defaultStyle);
                        for (File file : droppedFiles) {
                            print(textPane, "    " + file.getAbsolutePath() + "\n", stringStyle);
                        }
                        print(textPane, "\nPlease wait...\n", commentStyle);

                        // Scan directories in a separate thread
                        SwingUtilities.invokeLater(() -> scanDirectories(droppedFiles, textPane));
                    } catch (Exception ex) {
                        ex.printStackTrace();
                    }
                }
            });

            // Set initial text
            print(textPane, "Drag and drop directories here to scan them.\n", defaultStyle);
        });
    }

    private static void setDropTargetRecursively(Component component, DropTargetAdapter adapter) {
        new DropTarget(component, adapter);
        if (component instanceof Container) {
            for (Component child : ((Container) component).getComponents()) {
                setDropTargetRecursively(child, adapter);
            }
        }
    }

    private static void scanDirectories(List<File> directories, JTextPane textPane) {
        // Header
        print(textPane, "\n", commentStyle);
        print(textPane, "\n", commentStyle);
        print(textPane, "\n", commentStyle);
        print(textPane, "===============================\n", commentStyle);
        print(textPane, "=====    Pixel Scanner    =====\n", commentStyle);
        print(textPane, "===============================\n", commentStyle);
        print(textPane, "\n", commentStyle);

        // Iterate over directories
        for (File directory : directories) {
            if (!directory.isDirectory()) continue;

            // Count pixels
            long totalPixels = scanDirectoryRecursively(directory);
            HashMap<String, Long> pixelsPerSubdirectory = new HashMap<>();
            for (var file : Objects.requireNonNull(directory.listFiles())) {
                if (file.isDirectory()) {
                    long pixels = scanDirectoryRecursively(file);
                    pixelsPerSubdirectory.put(file.getName(), pixels);
                }
            }

            // Sort subdirectories
            var stream = pixelsPerSubdirectory.entrySet().stream().sorted((v1, v2) -> v2.getValue().compareTo(v1.getValue()));

            /*
                Print
             */
            print(textPane, "Directory: ", defaultStyle);
            print(textPane, directory.getAbsolutePath() + "\n", stringStyle);

            print(textPane, "Total pixels: ", defaultStyle);
            print(textPane, "%d px²\n".formatted((long) Math.sqrt(totalPixels)), pixelsStyle);

            print(textPane, "Content:\n", commentStyle);
            stream.forEachOrdered(e -> {
                var name = e.getKey();
                var pixels = e.getValue();
                float percentage = totalPixels == 0 ? 0 : (float) pixels / totalPixels;
                float friendlyPercentage = percentage * 100f;
                var isHighPercentage = percentage >= HIGH_PERCENTAGE_THRESHOLD;
                var percentageStyle = isHighPercentage ? highPercentageStyle : lowPercentageStyle;

                print(textPane, "    " + name + ": ", defaultStyle);
                print(textPane, "%d px²".formatted((long) Math.sqrt(pixels)), pixelsStyle);
                print(textPane, " (", defaultStyle);
                print(textPane, "%.0f%%".formatted(friendlyPercentage), percentageStyle);
                print(textPane, ")\n", defaultStyle);
            });
            print(textPane, "\n", defaultStyle);
        }
    }

    private static void print(JTextPane textPane, String text, AttributeSet attributes) {
        var doc = textPane.getStyledDocument();
        try {
            doc.insertString(doc.getLength(), text, attributes);
        } catch (BadLocationException e) {
            throw new RuntimeException(e);
        }
    }

    private static long scanDirectoryRecursively(File dir) {
        long count = 0;
        for (var file : Objects.requireNonNull(dir.listFiles())) {
            if (file.isFile() && file.getName().endsWith(".png")) {
                try {
                    BufferedImage image;
                    image = ImageIO.read(file);
                    count += (long) image.getWidth() * image.getHeight();
                } catch (IOException e) {
                    throw new RuntimeException(e);
                }
            } else if (file.isDirectory()) {
                count += scanDirectoryRecursively(file);
            }
        }
        return count;
    }

}