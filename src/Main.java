import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.jsoup.nodes.Element;
import org.jsoup.select.Elements;

import javax.swing.*;
import javax.swing.table.DefaultTableModel;
import java.awt.*;
import java.net.HttpURLConnection;
import java.net.MalformedURLException;
import java.net.URL;
import java.net.URLConnection;
import java.sql.*;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.Stack;

public class Main {
    private JFrame frame;
    private DefaultListModel<String> model;
    private JButton next, back, go, history;
    private JList<String> list;
    private JTextField url;
    private Stack<String> linksStack = new Stack<>();
    private int currentLinkInStack = -1;
    private JLabel imagesCount, imagesSize;
    private JPanel centerPanel;

    private void createAndShowGUI() {
        frame = new JFrame("Linki");
        frame.setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
        frame.setMinimumSize(new Dimension(400, 400));
        frame.getContentPane().setLayout(new BorderLayout());

        JPanel topPanel = new JPanel();
        topPanel.setLayout(new BorderLayout());

        JPanel leftPanel = new JPanel();
        back = new JButton(new ImageIcon(getClass().getResource("res/back-arrow.png")));
        back.addActionListener(e -> back());
        leftPanel.add(back);
        next = new JButton(new ImageIcon(getClass().getResource("res/forward-arrow.png")));
        next.addActionListener(e -> next());
        leftPanel.add(next);
        topPanel.add(leftPanel, BorderLayout.LINE_START);

        url = new JTextField();
        url.addActionListener(e -> onUrl());
        topPanel.add(url, BorderLayout.CENTER);

        JPanel rightPanel = new JPanel();
        go = new JButton(new ImageIcon(getClass().getResource("res/keyboard-right-arrow-button.png")));
        go.addActionListener(e -> onUrl());
        rightPanel.add(go);
        history = new JButton(new ImageIcon(getClass().getResource("res/history-clock-button.png")));
        history.addActionListener(e -> showHistory());
        rightPanel.add(history);
        topPanel.add(rightPanel, BorderLayout.LINE_END);

        frame.getContentPane().add(topPanel, BorderLayout.PAGE_START);

        centerPanel = new JPanel(new CardLayout());
        model = new DefaultListModel<>();
        list = new JList<>(model);
        list.setSelectionMode(ListSelectionModel.SINGLE_SELECTION);
        list.addListSelectionListener(e -> onListSelection());
        centerPanel.add(new JScrollPane(list));
        ImageIcon loading = new ImageIcon(getClass().getResource("res/loading.gif"));
        centerPanel.add(new JLabel("loading", loading, JLabel.CENTER));
        frame.getContentPane().add(centerPanel, BorderLayout.CENTER);

        JPanel bottomPanel = new JPanel();
        bottomPanel.add(new JLabel("images count: "));
        imagesCount = new JLabel("");
        bottomPanel.add(imagesCount);
        bottomPanel.add(new JLabel("images size: "));
        imagesSize = new JLabel("");
        bottomPanel.add(imagesSize);
        frame.getContentPane().add(bottomPanel, BorderLayout.PAGE_END);

        frame.pack();
        frame.setVisible(true);

        updateButtons();
    }

    private void updateButtons() {
        next.setEnabled(currentLinkInStack != linksStack.size() - 1);
        back.setEnabled(currentLinkInStack >= 1);
    }

    private void next() {
        if (currentLinkInStack < linksStack.size() - 1) {
            ++currentLinkInStack;
            runPageFromStack(currentLinkInStack);
        }
    }

    private void back() {
        if (currentLinkInStack > 0) {
            --currentLinkInStack;
            runPageFromStack(currentLinkInStack);
        }
    }

    private void runPageFromStack(int pageNum) {
        String page = linksStack.get(pageNum);
        run(page);
        url.setText(page);
    }

    private void addLinkToStack(String link) {
        while (linksStack.size() - 1 > currentLinkInStack)
            linksStack.pop();
        linksStack.push(link);
        ++currentLinkInStack;
    }

    private void onListSelection() {
        String selected = list.getSelectedValue();
        if (selected == null) return;
        run(selected);
        url.setText(selected);
        addLinkToStack(selected);
    }

    private void onUrl() {
        String link = url.getText();
        run(link);
        addLinkToStack(link);
    }

    private void run(String url) {
        blockGUI(false);
        model.clear();
        imagesCount.setText("");
        imagesSize.setText("");
        CardLayout cl = (CardLayout) centerPanel.getLayout();
        cl.next(centerPanel);

        class WorkerThreadResult {
            ArrayList<String> links = new ArrayList<>();
            int imagesCount, imagesSize;
        }

        SwingWorker<WorkerThreadResult, Void> worker = new SwingWorker<WorkerThreadResult, Void>() {
            @Override
            protected WorkerThreadResult doInBackground() throws Exception {
                addLinkToDatabase(url);
                WorkerThreadResult result = new WorkerThreadResult();
                URL oracle = new URL(url);
                URLConnection yc = oracle.openConnection();
                Document doc = Jsoup.parse(yc.getInputStream(), "utf-8", url);
                Elements links = doc.select("a");
                for (Element e : links) {
                    result.links.add(e.attr("abs:href"));
                }
                Elements images = doc.select("img");
                HashSet<String> linksSet = new HashSet<>();
                for (Element e : images) {
                    String imageDir = e.attr("abs:src");
                    if (linksSet.contains(imageDir)) continue;
                    //na stronie mogą być też <img> które są źle sformułowane
                    //ignorujemy je
                    try {
                        URL imageURL = new URL(imageDir);
                        HttpURLConnection imageConnection = (HttpURLConnection) imageURL.openConnection();
                        imageConnection.setRequestMethod("HEAD");
                        int len = imageConnection.getContentLength();
                        result.imagesSize += len;
                        linksSet.add(imageDir);
                    } catch (Exception ex) {}
                }
                result.imagesCount = linksSet.size();
                return result;
            }

            @Override
            protected void done() {
                WorkerThreadResult result;
                try {
                    result = get();
                }
                catch (Exception e) {
                    if(e.getCause() instanceof MalformedURLException)
                        JOptionPane.showMessageDialog(frame, "Malformed URL: " + e.getCause().getMessage(),
                                "Malformed URL", JOptionPane.ERROR_MESSAGE);
                    else
                        JOptionPane.showMessageDialog(frame, "Error loading page: " + e.getMessage(),
                                "Error loading page", JOptionPane.ERROR_MESSAGE);
                    return;
                }
                finally {
                    CardLayout cl = (CardLayout) centerPanel.getLayout();
                    cl.next(centerPanel);
                    blockGUI(true);
                    updateButtons();
                }
                for (String s : result.links)
                    model.addElement(s);
                imagesCount.setText("" + result.imagesCount);
                imagesSize.setText("" + result.imagesSize);
            }
        };
        worker.execute();
    }

    private void blockGUI(boolean enabled) {
        back.setEnabled(enabled);
        next.setEnabled(enabled);
        url.setEnabled(enabled);
        go.setEnabled(enabled);
        history.setEnabled(enabled);
    }

    private void createDatabase() {
        String sql = "CREATE TABLE IF NOT EXISTS links(" +
                "visit_time TIMESTAMP DEFAULT CURRENT_TIMESTAMP, " +
                "url VARCHAR NOT NULL)";
        try (Connection conn = DriverManager.getConnection("jdbc:sqlite:my.db");
             Statement preparedStatement = conn.createStatement()) {
            preparedStatement.execute(sql);
        } catch (SQLException e) {
            e.printStackTrace();
        }
    }

    private void addLinkToDatabase(String url) {
        String sql = "INSERT INTO links(url) VALUES(?)";
        try (Connection conn = DriverManager.getConnection("jdbc:sqlite:my.db");
             PreparedStatement preparedStatement = conn.prepareStatement(sql)) {
            preparedStatement.setString(1, url);
            preparedStatement.setString(1, url);
            preparedStatement.executeUpdate();
        } catch (SQLException e) {
            e.printStackTrace();
        }
    }

    private void showHistory() {
        JDialog dialog = new JDialog(frame, "History", true);
        dialog.setMinimumSize(new Dimension(400, 400));
        JTable table = new JTable();
        try(Connection conn = DriverManager.getConnection("jdbc:sqlite:my.db");
            Statement preparedStatement = conn.createStatement()) {
            String sql = "SELECT visit_time, url FROM links ORDER BY datetime(visit_time);";
            ResultSet rs = preparedStatement.executeQuery(sql);
            DefaultTableModel model = (DefaultTableModel) table.getModel();
            model.addColumn("time");
            model.addColumn("url");
            while (rs.next()) {
                model.addRow(new Object[]{rs.getString("visit_time"),
                        rs.getString("url")});
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
        table.setSelectionMode(ListSelectionModel.SINGLE_SELECTION);
        table.getSelectionModel().addListSelectionListener(event -> {
            if (table.getSelectedRow() == -1) return;
            dialog.dispose();
            String selected = table.getValueAt(table.getSelectedRow(), 1).toString();
            run(selected);
            url.setText(selected);
            addLinkToStack(selected);
        });
        dialog.add(new JScrollPane(table));
        dialog.setVisible(true);
    }

    public static void main(String[] args) {
        javax.swing.SwingUtilities.invokeLater(() -> {
            Main main = new Main();
            main.createDatabase();
            main.createAndShowGUI();
        });
    }
}
