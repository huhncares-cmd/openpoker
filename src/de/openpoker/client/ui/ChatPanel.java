package de.openpoker.client.ui;

import java.awt.BorderLayout;
import java.awt.Color;
import java.awt.Dimension;
import java.awt.Font;
import java.util.List;
import java.util.function.Predicate;
import javax.swing.JButton;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.JScrollPane;
import javax.swing.JTextArea;
import javax.swing.JTextField;
import javax.swing.border.EmptyBorder;

public final class ChatPanel extends JPanel {
    private static final long serialVersionUID = 1L;

    private final JTextArea chatArea = new JTextArea();
    private final JTextField chatInput = new JTextField(12);
    private final JButton sendBtn = new ModernButton("Senden", new Color(55, 65, 85), new Color(40, 48, 65));
    private final Predicate<String> messageSender;

    public ChatPanel(Predicate<String> messageSender) {
        super(new BorderLayout());
        this.messageSender = messageSender;

        setPreferredSize(new Dimension(280, 0));
        setBackground(new Color(20, 23, 31));
        setBorder(new EmptyBorder(12, 10, 12, 12));

        JLabel chatTitle = new JLabel("TISCH-CHAT & LOGS", JLabel.CENTER);
        chatTitle.setFont(new Font("SansSerif", Font.BOLD, 13));
        chatTitle.setForeground(new Color(190, 195, 210));
        chatTitle.setBorder(new EmptyBorder(0, 0, 10, 0));
        add(chatTitle, BorderLayout.NORTH);

        chatArea.setEditable(false);
        chatArea.setLineWrap(true);
        chatArea.setWrapStyleWord(true);
        chatArea.setBackground(new Color(13, 15, 20));
        chatArea.setForeground(new Color(225, 230, 240));
        chatArea.setFont(new Font("SansSerif", Font.PLAIN, 12));
        chatArea.setBorder(new EmptyBorder(6, 8, 6, 8));

        JScrollPane scrollPane = new JScrollPane(chatArea);
        scrollPane.setBorder(null);
        add(scrollPane, BorderLayout.CENTER);

        JPanel inputPanel = new JPanel(new BorderLayout(6, 0));
        inputPanel.setOpaque(false);
        inputPanel.setBorder(new EmptyBorder(10, 0, 0, 0));

        chatInput.setBackground(new Color(28, 32, 44));
        chatInput.setForeground(Color.WHITE);
        chatInput.setCaretColor(Color.WHITE);
        chatInput.setFont(new Font("SansSerif", Font.PLAIN, 12));
        chatInput.setBorder(new EmptyBorder(6, 8, 6, 8));
        chatInput.addActionListener(e -> sendChat());

        sendBtn.setPreferredSize(new Dimension(80, 32));
        sendBtn.addActionListener(e -> sendChat());

        inputPanel.add(chatInput, BorderLayout.CENTER);
        inputPanel.add(sendBtn, BorderLayout.EAST);
        add(inputPanel, BorderLayout.SOUTH);
    }

    public void setChatEnabled(boolean enabled) {
        chatInput.setEnabled(enabled);
        sendBtn.setEnabled(enabled);
    }

    public void updateMessages(List<String> messages) {
        chatArea.setText(String.join("\n", messages));
        chatArea.setCaretPosition(chatArea.getDocument().getLength());
    }

    private void sendChat() {
        String text = chatInput.getText().trim();
        if (!text.isEmpty() && messageSender.test(text)) {
            chatInput.setText("");
        }
    }
}
