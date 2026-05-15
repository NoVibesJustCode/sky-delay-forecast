package es.ulpgc.dacd.skydelay.business.view;

import javax.swing.*;
import java.awt.*;
import java.io.File;
import java.net.URI;

public class MainLauncher extends JFrame {

    public MainLauncher() {
        setTitle("SkyDelay - Professional Portal");
        setSize(400, 500);
        setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
        setLocationRelativeTo(null);
        setLayout(new GridLayout(5, 1, 10, 10));

        JLabel title = new JLabel("SkyDelay Control Center", SwingConstants.CENTER);
        title.setFont(new Font("Arial", Font.BOLD, 20));
        add(title);

        JButton mapButton = createButton("🌐 Open Public Map", "http://localhost:7070");
        JButton dashboardButton = createDashboardButton();
        JButton guideButton = createPdfButton("📄 User Guide (PDF)", "docs/user_guide.pdf");
        JButton exitButton = new JButton("❌ Exit System");

        exitButton.addActionListener(e -> System.exit(0));

        add(mapButton);
        add(dashboardButton);
        add(guideButton);
        add(exitButton);
    }

    private JButton createButton(String text, String url) {
        JButton button = new JButton(text);
        button.addActionListener(e -> openWebpage(url));
        return button;
    }

    private JButton createDashboardButton() {
        JButton button = new JButton("📊 Business Dashboard (Subscription)");
        button.addActionListener(e -> {
            JPasswordField pf = new JPasswordField();
            int okCxl = JOptionPane.showConfirmDialog(null, pf, "Enter Business License Key", JOptionPane.OK_CANCEL_OPTION, JOptionPane.PLAIN_MESSAGE);

            if (okCxl == JOptionPane.OK_OPTION) {
                String password = new String(pf.getPassword());
                if ("admin123".equals(password)) {
                    openWebpage("http://localhost:8080");
                } else {
                    JOptionPane.showMessageDialog(this, "Invalid License Key", "Access Denied", JOptionPane.ERROR_MESSAGE);
                }
            }
        });
        return button;
    }

    private JButton createPdfButton(String text, String path) {
        JButton button = new JButton(text);
        button.addActionListener(e -> {
            try {
                File pdfFile = new File(path);
                if (pdfFile.exists()) {
                    Desktop.getDesktop().open(pdfFile);
                } else {
                    JOptionPane.showMessageDialog(this, "PDF Guide not found at: " + path);
                }
            } catch (Exception ex) {
                ex.printStackTrace();
            }
        });
        return button;
    }

    private void openWebpage(String url) {
        try {
            Desktop.getDesktop().browse(new URI(url));
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    public static void main(String[] args) {
        SwingUtilities.invokeLater(() -> new MainLauncher().setVisible(true));
    }
}
