package es.ulpgc.dacd.skydelay.business.view;

import javax.swing.*;
import javax.swing.border.EmptyBorder;
import java.awt.*;
import java.awt.event.*;
import java.awt.geom.RoundRectangle2D;
import java.awt.image.BufferedImage;
import java.io.File;
import java.io.InputStream;
import java.net.URI;
import javax.imageio.ImageIO;

public class MainLauncher extends JFrame {

    private static final Color PRIMARY      = new Color(30, 58, 138);
    private static final Color PRIMARY_DARK = new Color(23, 37, 84);
    private static final Color ACCENT       = new Color(59, 130, 246);
    private static final Color SUCCESS      = new Color(34, 197, 94);
    private static final Color WARNING      = new Color(245, 158, 11);
    private static final Color DANGER       = new Color(239, 68, 68);
    private static final Color BG_DARK      = new Color(15, 23, 42);
    private static final Color BG_CARD      = new Color(30, 41, 59);
    private static final Color TEXT_PRIMARY = new Color(241, 245, 249);
    private static final Color TEXT_MUTED   = new Color(148, 163, 184);

    public MainLauncher() {
        setTitle("SkyDelay — Flight Intelligence Platform");
        setSize(520, 680);
        setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
        setLocationRelativeTo(null);
        setUndecorated(true);
        setShape(new RoundRectangle2D.Double(0, 0, 520, 680, 24, 24));

        JPanel root = new JPanel(new BorderLayout()) {
            @Override
            protected void paintComponent(Graphics g) {
                super.paintComponent(g);
                Graphics2D g2 = (Graphics2D) g.create();
                g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
                GradientPaint gp = new GradientPaint(0, 0, BG_DARK, 0, getHeight(), PRIMARY_DARK);
                g2.setPaint(gp);
                g2.fillRoundRect(0, 0, getWidth(), getHeight(), 24, 24);
                g2.dispose();
            }
        };
        root.setBorder(new EmptyBorder(0, 0, 0, 0));
        setContentPane(root);

        JPanel header = createHeader();
        root.add(header, BorderLayout.NORTH);

        JPanel center = new JPanel();
        center.setOpaque(false);
        center.setLayout(new BoxLayout(center, BoxLayout.Y_AXIS));
        center.setBorder(new EmptyBorder(20, 40, 20, 40));

        center.add(createActionCard(
                "✈️  Public Flight Map",
                "Real-time predictions on an interactive map",
                "http://localhost:8080",
                ACCENT, false
        ));
        center.add(Box.createVerticalStrut(16));

        center.add(createActionCard(
                "📊  Business Dashboard",
                "Analytics, charts & delay intelligence",
                "http://localhost:7070",
                SUCCESS, true
        ));
        center.add(Box.createVerticalStrut(16));

        center.add(createActionCard(
                "📄  User Guide",
                "Open the PDF documentation",
                "docs/user_guide.pdf",
                WARNING, false
        ));

        root.add(center, BorderLayout.CENTER);

        JPanel footer = createFooter();
        root.add(footer, BorderLayout.SOUTH);

        addDragSupport(header);
    }

    private JPanel createHeader() {
        JPanel header = new JPanel(new BorderLayout());
        header.setOpaque(false);
        header.setBorder(new EmptyBorder(30, 40, 10, 40));

        JPanel titleBlock = new JPanel();
        titleBlock.setOpaque(false);
        titleBlock.setLayout(new BoxLayout(titleBlock, BoxLayout.Y_AXIS));

        JLabel logoIcon = new JLabel();
        logoIcon.setAlignmentX(Component.LEFT_ALIGNMENT);
        try {
            InputStream is = getClass().getClassLoader().getResourceAsStream("skydelay_logo.png");
            if (is != null) {
                BufferedImage img = ImageIO.read(is);
                logoIcon.setIcon(new ImageIcon(img.getScaledInstance(48, 48, Image.SCALE_SMOOTH)));
            }
        } catch (Exception ignored) {}

        JLabel logo = new JLabel("SkyDelay");
        logo.setFont(new Font("Segoe UI", Font.BOLD, 32));
        logo.setForeground(TEXT_PRIMARY);
        logo.setAlignmentX(Component.LEFT_ALIGNMENT);

        JLabel subtitle = new JLabel("Flight Delay Intelligence Platform");
        subtitle.setFont(new Font("Segoe UI", Font.PLAIN, 14));
        subtitle.setForeground(TEXT_MUTED);
        subtitle.setAlignmentX(Component.LEFT_ALIGNMENT);

        JLabel version = new JLabel("v1.0 — Spanish Airport Network");
        version.setFont(new Font("Segoe UI", Font.ITALIC, 11));
        version.setForeground(new Color(100, 116, 139));
        version.setAlignmentX(Component.LEFT_ALIGNMENT);

        titleBlock.add(logoIcon);
        titleBlock.add(Box.createVerticalStrut(8));
        titleBlock.add(logo);
        titleBlock.add(Box.createVerticalStrut(4));
        titleBlock.add(subtitle);
        titleBlock.add(Box.createVerticalStrut(2));
        titleBlock.add(version);

        header.add(titleBlock, BorderLayout.WEST);

        JLabel closeBtn = new JLabel("✕");
        closeBtn.setFont(new Font("Segoe UI", Font.BOLD, 18));
        closeBtn.setForeground(TEXT_MUTED);
        closeBtn.setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
        closeBtn.addMouseListener(new MouseAdapter() {
            @Override public void mouseClicked(MouseEvent e) { System.exit(0); }
            @Override public void mouseEntered(MouseEvent e) { closeBtn.setForeground(DANGER); }
            @Override public void mouseExited(MouseEvent e) { closeBtn.setForeground(TEXT_MUTED); }
        });
        JPanel closePanel = new JPanel(new FlowLayout(FlowLayout.RIGHT, 0, 0));
        closePanel.setOpaque(false);
        closePanel.add(closeBtn);
        header.add(closePanel, BorderLayout.EAST);

        JPanel statusPanel = new JPanel(new FlowLayout(FlowLayout.LEFT, 0, 0));
        statusPanel.setOpaque(false);
        statusPanel.setBorder(new EmptyBorder(12, 0, 0, 0));
        JLabel statusDot = new JLabel("●");
        statusDot.setFont(new Font("Segoe UI", Font.PLAIN, 10));
        statusDot.setForeground(SUCCESS);
        JLabel statusText = new JLabel("  System Online — All services running");
        statusText.setFont(new Font("Segoe UI", Font.PLAIN, 12));
        statusText.setForeground(SUCCESS);
        statusPanel.add(statusDot);
        statusPanel.add(statusText);

        JPanel headerBottom = new JPanel(new BorderLayout());
        headerBottom.setOpaque(false);
        headerBottom.add(statusPanel, BorderLayout.WEST);
        header.add(headerBottom, BorderLayout.SOUTH);

        return header;
    }

    private JPanel createActionCard(String title, String desc, String target,
                                    Color accentColor, boolean requiresAuth) {
        JPanel card = new JPanel(new BorderLayout()) {
            @Override
            protected void paintComponent(Graphics g) {
                super.paintComponent(g);
                Graphics2D g2 = (Graphics2D) g.create();
                g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
                g2.setColor(BG_CARD);
                g2.fillRoundRect(0, 0, getWidth(), getHeight(), 16, 16);
                // Accent left bar
                g2.setColor(accentColor);
                g2.fillRoundRect(0, 8, 4, getHeight() - 16, 4, 4);
                g2.dispose();
            }
        };
        card.setOpaque(false);
        card.setPreferredSize(new Dimension(440, 90));
        card.setMaximumSize(new Dimension(Integer.MAX_VALUE, 90));
        card.setBorder(new EmptyBorder(16, 24, 16, 24));
        card.setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));

        JPanel textPanel = new JPanel();
        textPanel.setOpaque(false);
        textPanel.setLayout(new BoxLayout(textPanel, BoxLayout.Y_AXIS));

        JLabel titleLabel = new JLabel(title);
        titleLabel.setFont(new Font("Segoe UI", Font.BOLD, 16));
        titleLabel.setForeground(TEXT_PRIMARY);
        titleLabel.setAlignmentX(Component.LEFT_ALIGNMENT);

        JLabel descLabel = new JLabel(desc);
        descLabel.setFont(new Font("Segoe UI", Font.PLAIN, 12));
        descLabel.setForeground(TEXT_MUTED);
        descLabel.setAlignmentX(Component.LEFT_ALIGNMENT);

        textPanel.add(titleLabel);
        textPanel.add(Box.createVerticalStrut(4));
        textPanel.add(descLabel);

        if (requiresAuth) {
            JLabel badge = new JLabel("🔒 Licensed");
            badge.setFont(new Font("Segoe UI", Font.PLAIN, 10));
            badge.setForeground(WARNING);
            badge.setAlignmentX(Component.LEFT_ALIGNMENT);
            textPanel.add(Box.createVerticalStrut(4));
            textPanel.add(badge);
        }

        card.add(textPanel, BorderLayout.CENTER);

        JLabel arrow = new JLabel("→");
        arrow.setFont(new Font("Segoe UI", Font.BOLD, 20));
        arrow.setForeground(accentColor);
        card.add(arrow, BorderLayout.EAST);

        // Hover effect
        card.addMouseListener(new MouseAdapter() {
            @Override
            public void mouseEntered(MouseEvent e) {
                card.setBorder(new EmptyBorder(14, 22, 14, 22));
                card.repaint();
            }

            @Override
            public void mouseExited(MouseEvent e) {
                card.setBorder(new EmptyBorder(16, 24, 16, 24));
                card.repaint();
            }

            @Override
            public void mouseClicked(MouseEvent e) {
                if (target.endsWith(".pdf")) {
                    openPdf(target);
                } else if (requiresAuth) {
                    openWithAuth(target);
                } else {
                    openWebpage(target);
                }
            }
        });

        return card;
    }

    private JPanel createFooter() {
        JPanel footer = new JPanel(new BorderLayout());
        footer.setOpaque(false);
        footer.setBorder(new EmptyBorder(10, 40, 30, 40));

        JLabel credits = new JLabel("DACD · ULPGC · 2025–2026");
        credits.setFont(new Font("Segoe UI", Font.PLAIN, 11));
        credits.setForeground(new Color(71, 85, 105));
        credits.setHorizontalAlignment(SwingConstants.CENTER);

        JButton exitBtn = new JButton("Shutdown System");
        exitBtn.setFont(new Font("Segoe UI", Font.BOLD, 12));
        exitBtn.setForeground(DANGER);
        exitBtn.setBackground(new Color(30, 41, 59));
        exitBtn.setBorder(BorderFactory.createCompoundBorder(
                BorderFactory.createLineBorder(DANGER, 1, true),
                new EmptyBorder(8, 20, 8, 20)
        ));
        exitBtn.setFocusPainted(false);
        exitBtn.setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
        exitBtn.addActionListener(e -> System.exit(0));
        exitBtn.addMouseListener(new MouseAdapter() {
            @Override public void mouseEntered(MouseEvent e) {
                exitBtn.setBackground(DANGER);
                exitBtn.setForeground(Color.WHITE);
            }
            @Override public void mouseExited(MouseEvent e) {
                exitBtn.setBackground(new Color(30, 41, 59));
                exitBtn.setForeground(DANGER);
            }
        });

        JPanel btnPanel = new JPanel(new FlowLayout(FlowLayout.CENTER));
        btnPanel.setOpaque(false);
        btnPanel.add(exitBtn);

        footer.add(btnPanel, BorderLayout.CENTER);
        footer.add(credits, BorderLayout.SOUTH);
        return footer;
    }

    private void openWithAuth(String url) {
        JPasswordField pf = new JPasswordField();
        pf.setFont(new Font("Segoe UI", Font.PLAIN, 14));
        int result = JOptionPane.showConfirmDialog(this, pf,
                "Enter Business License Key", JOptionPane.OK_CANCEL_OPTION, JOptionPane.PLAIN_MESSAGE);
        if (result == JOptionPane.OK_OPTION) {
            if ("admin123".equals(new String(pf.getPassword()))) {
                openWebpage(url);
            } else {
                JOptionPane.showMessageDialog(this,
                        "Invalid License Key", "Access Denied", JOptionPane.ERROR_MESSAGE);
            }
        }
    }

    private void openPdf(String path) {
        try {
            File pdfFile = new File(path);
            if (pdfFile.exists()) {
                Desktop.getDesktop().open(pdfFile);
            } else {
                JOptionPane.showMessageDialog(this,
                        "PDF Guide not found at: " + path, "File Not Found", JOptionPane.WARNING_MESSAGE);
            }
        } catch (Exception ex) {
            ex.printStackTrace();
        }
    }

    private void openWebpage(String url) {
        try {
            Desktop.getDesktop().browse(new URI(url));
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    private void addDragSupport(JPanel panel) {
        final Point[] dragPoint = {null};
        panel.addMouseListener(new MouseAdapter() {
            @Override public void mousePressed(MouseEvent e) {
                dragPoint[0] = e.getPoint();
            }
        });
        panel.addMouseMotionListener(new MouseMotionAdapter() {
            @Override public void mouseDragged(MouseEvent e) {
                if (dragPoint[0] != null) {
                    Point loc = getLocation();
                    setLocation(loc.x + e.getX() - dragPoint[0].x,
                                loc.y + e.getY() - dragPoint[0].y);
                }
            }
        });
    }

    public static void main(String[] args) {
        try {
            UIManager.setLookAndFeel(UIManager.getSystemLookAndFeelClassName());
        } catch (Exception ignored) {}
        SwingUtilities.invokeLater(() -> new MainLauncher().setVisible(true));
    }
}
