import javax.swing.*;
import javax.swing.border.EmptyBorder;
import javax.swing.table.DefaultTableModel;
import java.awt.*;
import java.io.*;
import java.nio.file.*;
import java.nio.file.attribute.BasicFileAttributes;
import java.security.MessageDigest;
import java.text.SimpleDateFormat;
import java.util.*;
import java.util.List;
import java.util.stream.Stream;

/**
 * Smart File Organizer
 * A dependency-free desktop file organizer built with Java Swing and Java NIO.
 * Compatible with JDK 8+.
 */
public class SmartFileOrganizer extends JFrame {
    private final DefaultTableModel model = new DefaultTableModel(
            new Object[]{"File name", "Type", "Size", "Last modified", "Location", "Status"}, 0) {
        public boolean isCellEditable(int row, int column) { return false; }
    };
    private final JTable table = new JTable(model);
    private final JLabel folderLabel = new JLabel("No folder selected");
    private final JLabel statusLabel = new JLabel("Choose a folder to get started.");
    private final JLabel countLabel = new JLabel("0 files");
    private final JButton scanButton = new JButton("Scan folder");
    private final JButton duplicatesButton = new JButton("Find duplicates");
    private final JButton previewButton = new JButton("Preview organize");
    private final JButton organizeButton = new JButton("Organize files");
    private final JButton undoButton = new JButton("Undo");
    private final JButton redoButton = new JButton("Redo");
    private final JButton trashButton = new JButton("Move duplicates to Trash");
    private Path selectedFolder;
    private final List<Path> scannedFiles = new ArrayList<Path>();
    private final List<MoveRecord> pendingMoves = new ArrayList<MoveRecord>();
    private final Set<Path> duplicateFiles = new LinkedHashSet<Path>();
    private final SimpleDateFormat dateFormat = new SimpleDateFormat("yyyy-MM-dd HH:mm");

    private static class MoveRecord {
        final Path original;
        final Path moved;
        MoveRecord(Path original, Path moved) { this.original = original; this.moved = moved; }
    }

    public SmartFileOrganizer() {
        super("Smart File Organizer");
        setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
        setMinimumSize(new Dimension(1050, 650));
        setSize(1200, 740);
        setLocationRelativeTo(null);
        buildUI();
        updateButtons();
    }

    private void buildUI() {
        JPanel root = new JPanel(new BorderLayout(16, 16));
        root.setBorder(new EmptyBorder(18, 20, 14, 20));
        root.setBackground(new Color(241, 244, 255));
        setContentPane(root);

        // Colorful gradient hero header
        JPanel header = new JPanel(new BorderLayout(12, 0)) {
            protected void paintComponent(Graphics g) {
                super.paintComponent(g);
                Graphics2D g2 = (Graphics2D) g.create();
                g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
                g2.setPaint(new GradientPaint(0, 0, new Color(91, 61, 190),
                        getWidth(), getHeight(), new Color(25, 154, 190)));
                g2.fillRoundRect(0, 0, getWidth(), getHeight(), 24, 24);
                g2.dispose();
            }
        };
        header.setOpaque(false);
        header.setBorder(new EmptyBorder(22, 24, 22, 24));

        JLabel title = new JLabel("✦  Smart File Organizer");
        title.setFont(new Font("Segoe UI", Font.BOLD, 27));
        title.setForeground(Color.WHITE);
        JLabel subtitle = new JLabel("A smarter way to scan, clean and organize your files");
        subtitle.setFont(new Font("Segoe UI", Font.PLAIN, 13));
        subtitle.setForeground(new Color(231, 239, 255));
        JPanel titlePanel = new JPanel();
        titlePanel.setOpaque(false);
        titlePanel.setLayout(new BoxLayout(titlePanel, BoxLayout.Y_AXIS));
        titlePanel.add(title);
        titlePanel.add(Box.createVerticalStrut(7));
        titlePanel.add(subtitle);
        header.add(titlePanel, BorderLayout.WEST);

        JButton chooseButton = makeButton("＋  Choose folder", new Color(255, 255, 255));
        chooseButton.setForeground(new Color(75, 61, 160));
        chooseButton.setBorder(BorderFactory.createEmptyBorder(12, 18, 12, 18));
        chooseButton.addActionListener(e -> chooseFolder());
        header.add(chooseButton, BorderLayout.EAST);
        root.add(header, BorderLayout.NORTH);

        JPanel center = new JPanel(new BorderLayout(0, 13));
        center.setOpaque(false);

        // Working-folder information card
        JPanel folderCard = new JPanel(new BorderLayout(12, 0));
        folderCard.setBackground(Color.WHITE);
        folderCard.setBorder(BorderFactory.createCompoundBorder(
                BorderFactory.createLineBorder(new Color(224, 226, 245)),
                new EmptyBorder(14, 17, 14, 17)));
        JLabel folderCaption = new JLabel("CURRENT WORKSPACE");
        folderCaption.setFont(new Font("Segoe UI", Font.BOLD, 10));
        folderCaption.setForeground(new Color(111, 100, 177));
        folderLabel.setFont(new Font("Segoe UI", Font.PLAIN, 13));
        folderLabel.setForeground(new Color(45, 48, 75));
        JPanel folderTexts = new JPanel();
        folderTexts.setOpaque(false);
        folderTexts.setLayout(new BoxLayout(folderTexts, BoxLayout.Y_AXIS));
        folderTexts.add(folderCaption);
        folderTexts.add(Box.createVerticalStrut(6));
        folderTexts.add(folderLabel);
        folderCard.add(folderTexts, BorderLayout.CENTER);

        JPanel countPill = new JPanel(new FlowLayout(FlowLayout.CENTER, 8, 7));
        countPill.setBackground(new Color(235, 241, 255));
        JLabel countIcon = new JLabel("▦");
        countIcon.setFont(new Font("Segoe UI", Font.BOLD, 16));
        countIcon.setForeground(new Color(83, 91, 207));
        countLabel.setFont(new Font("Segoe UI", Font.BOLD, 12));
        countLabel.setForeground(new Color(65, 72, 155));
        countPill.add(countIcon);
        countPill.add(countLabel);
        folderCard.add(countPill, BorderLayout.EAST);
        center.add(folderCard, BorderLayout.NORTH);

        // File table styling
        table.setRowHeight(34);
        table.setFont(new Font("Segoe UI", Font.PLAIN, 12));
        table.setForeground(new Color(48, 54, 78));
        table.getTableHeader().setFont(new Font("Segoe UI", Font.BOLD, 12));
        table.getTableHeader().setBackground(new Color(91, 75, 170));
        table.getTableHeader().setForeground(Color.WHITE);
        table.getTableHeader().setPreferredSize(new Dimension(0, 38));
        table.setSelectionBackground(new Color(222, 226, 255));
        table.setSelectionForeground(new Color(44, 47, 103));
        table.setGridColor(new Color(235, 237, 247));
        table.setShowVerticalLines(false);
        table.setFillsViewportHeight(true);
        table.setAutoCreateRowSorter(true);
        table.setIntercellSpacing(new Dimension(0, 1));

        javax.swing.table.DefaultTableCellRenderer cellRenderer =
            new javax.swing.table.DefaultTableCellRenderer() {
                public Component getTableCellRendererComponent(JTable t, Object value,
                        boolean selected, boolean focus, int row, int column) {
                    Component c = super.getTableCellRendererComponent(t, value, selected, focus, row, column);
                    if (!selected) c.setBackground(row % 2 == 0 ? Color.WHITE : new Color(248, 249, 255));
                    setBorder(new EmptyBorder(0, 10, 0, 10));
                    if (column == 5 && value != null) {
                        String v = value.toString();
                        if ("DUPLICATE".equals(v)) setForeground(new Color(205, 70, 90));
                        else if ("SCANNED".equals(v)) setForeground(new Color(35, 145, 112));
                        else setForeground(new Color(85, 91, 120));
                    } else if (!selected) setForeground(new Color(48, 54, 78));
                    return c;
                }
            };
        for (int i = 0; i < table.getColumnCount(); i++) table.getColumnModel().getColumn(i).setCellRenderer(cellRenderer);
        JScrollPane scroll = new JScrollPane(table);
        scroll.setBorder(BorderFactory.createLineBorder(new Color(224, 226, 245)));
        scroll.getViewport().setBackground(Color.WHITE);
        center.add(scroll, BorderLayout.CENTER);

        // Bright action buttons grouped by purpose
        JPanel actions = new JPanel(new GridLayout(2, 4, 10, 10));
        actions.setOpaque(false);
        styleAction(scanButton, new Color(68, 105, 220));
        styleAction(duplicatesButton, new Color(222, 92, 133));
        styleAction(previewButton, new Color(135, 91, 205));
        styleAction(organizeButton, new Color(26, 157, 123));
        styleAction(undoButton, new Color(54, 139, 185));
        styleAction(redoButton, new Color(45, 164, 174));
        styleAction(trashButton, new Color(224, 126, 66));
        JButton clearButton = makeButton("✕  Clear results", new Color(112, 119, 145));
        actions.add(scanButton); actions.add(duplicatesButton);
        actions.add(previewButton); actions.add(organizeButton);
        actions.add(undoButton); actions.add(redoButton);
        actions.add(trashButton); actions.add(clearButton);
        center.add(actions, BorderLayout.SOUTH);
        root.add(center, BorderLayout.CENTER);

        JPanel footer = new JPanel(new BorderLayout());
        footer.setOpaque(false);
        statusLabel.setFont(new Font("Segoe UI", Font.PLAIN, 12));
        statusLabel.setForeground(new Color(78, 82, 117));
        footer.add(statusLabel, BorderLayout.WEST);
        JLabel safety = new JLabel("♥  Safe cleanup: duplicates go to .trash");
        safety.setFont(new Font("Segoe UI", Font.BOLD, 11));
        safety.setForeground(new Color(31, 143, 116));
        footer.add(safety, BorderLayout.EAST);
        root.add(footer, BorderLayout.SOUTH);

        scanButton.addActionListener(e -> scanFolder());
        duplicatesButton.addActionListener(e -> findDuplicates());
        previewButton.addActionListener(e -> previewOrganize());
        organizeButton.addActionListener(e -> executeOrganize());
        undoButton.addActionListener(e -> undo());
        redoButton.addActionListener(e -> redo());
        trashButton.addActionListener(e -> moveDuplicatesToTrash());
        clearButton.addActionListener(e -> clearResults());
    }

    private JButton makeButton(String text, Color color) {
        JButton b = new JButton(text);
        styleAction(b, color);
        return b;
    }

    private void styleAction(JButton b, Color color) {
        b.setFont(new Font("Segoe UI", Font.BOLD, 12));
        if (b.getForeground() == null || Color.BLACK.equals(b.getForeground())) b.setForeground(Color.WHITE);
        b.setBackground(color);
        b.setOpaque(true);
        b.setFocusPainted(false);
        b.setBorder(BorderFactory.createCompoundBorder(
                BorderFactory.createLineBorder(color.darker(), 1),
                BorderFactory.createEmptyBorder(11, 12, 11, 12)));
        b.setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
    }

    private void chooseFolder() {
        JFileChooser chooser = new JFileChooser();
        chooser.setFileSelectionMode(JFileChooser.DIRECTORIES_ONLY);
        chooser.setDialogTitle("Select the folder to organize");
        if (chooser.showOpenDialog(this) == JFileChooser.APPROVE_OPTION) {
            selectedFolder = chooser.getSelectedFile().toPath().toAbsolutePath().normalize();
            folderLabel.setText(selectedFolder.toString());
            scannedFiles.clear();
            duplicateFiles.clear();
            pendingMoves.clear();
            model.setRowCount(0);
            status("Folder selected. Click Scan folder.");
            updateButtons();
        }
    }

    private void scanFolder() {
        if (!ensureFolder()) return;
        setBusy(true);
        new SwingWorker<List<Path>, Void>() {
            protected List<Path> doInBackground() throws Exception {
                List<Path> files = new ArrayList<Path>();
                try (Stream<Path> walk = Files.walk(selectedFolder)) {
                    walk.filter(Files::isRegularFile)
                        .filter(p -> !p.startsWith(selectedFolder.resolve(".trash")))
                        .forEach(files::add);
                }
                return files;
            }
            protected void done() {
                try {
                    scannedFiles.clear();
                    scannedFiles.addAll(get());
                    duplicateFiles.clear();
                    showFiles(scannedFiles, Collections.<Path>emptySet(), "Scanned");
                    status("Scan complete. Found " + scannedFiles.size() + " files.");
                } catch (Exception ex) { showError("Could not scan folder", ex); }
                setBusy(false);
            }
        }.execute();
    }

    private void findDuplicates() {
        if (scannedFiles.isEmpty()) { info("Scan a folder first."); return; }
        setBusy(true);
        status("Checking file sizes and calculating SHA-256 hashes...");
        new SwingWorker<Set<Path>, Void>() {
            protected Set<Path> doInBackground() throws Exception {
                Map<Long, List<Path>> bySize = new HashMap<Long, List<Path>>();
                for (Path p : scannedFiles) {
                    long size = Files.size(p);
                    bySize.computeIfAbsent(size, k -> new ArrayList<Path>()).add(p);
                }
                Set<Path> duplicates = new LinkedHashSet<Path>();
                for (List<Path> group : bySize.values()) {
                    if (group.size() < 2) continue;
                    Map<String, List<Path>> byHash = new HashMap<String, List<Path>>();
                    for (Path p : group) {
                        String hash = sha256(p);
                        byHash.computeIfAbsent(hash, k -> new ArrayList<Path>()).add(p);
                    }
                    for (List<Path> same : byHash.values()) {
                        if (same.size() > 1) duplicates.addAll(same);
                    }
                }
                return duplicates;
            }
            protected void done() {
                try {
                    duplicateFiles.clear();
                    duplicateFiles.addAll(get());
                    showFiles(scannedFiles, duplicateFiles, "Duplicate");
                    status("Duplicate scan complete. " + duplicateFiles.size() + " duplicate file(s) identified.");
                } catch (Exception ex) { showError("Duplicate scan failed", ex); }
                setBusy(false);
            }
        }.execute();
    }

    private String sha256(Path path) throws Exception {
        MessageDigest md = MessageDigest.getInstance("SHA-256");
        try (InputStream in = Files.newInputStream(path)) {
            byte[] buffer = new byte[8192];
            int read;
            while ((read = in.read(buffer)) != -1) md.update(buffer, 0, read);
        }
        StringBuilder out = new StringBuilder();
        for (byte b : md.digest()) out.append(String.format("%02x", b & 0xff));
        return out.toString();
    }

    private void previewOrganize() {
        if (scannedFiles.isEmpty()) { info("Scan a folder first."); return; }
        pendingMoves.clear();
        StringBuilder preview = new StringBuilder();
        int count = 0;
        for (Path source : scannedFiles) {
            Path targetDir = selectedFolder.resolve(categoryFor(source));
            Path target = uniqueTarget(targetDir.resolve(source.getFileName()));
            if (!source.getParent().equals(targetDir)) {
                pendingMoves.add(new MoveRecord(source, target));
                preview.append(source.getFileName()).append("  →  ")
                       .append(selectedFolder.relativize(target)).append('\n');
                count++;
            }
        }
        JTextArea area = new JTextArea(preview.length() == 0 ? "All files are already organized." : preview.toString(), 18, 70);
        area.setEditable(false);
        area.setFont(new Font("Consolas", Font.PLAIN, 12));
        JScrollPane pane = new JScrollPane(area);
        JOptionPane.showMessageDialog(this, pane, "Organization preview — " + count + " planned move(s)",
                JOptionPane.INFORMATION_MESSAGE);
        status("Preview ready. No files were changed. Planned moves: " + count + ".");
        updateButtons();
    }

    private String categoryFor(Path file) {
        String name = file.getFileName().toString();
        int dot = name.lastIndexOf('.');
        if (dot <= 0 || dot == name.length() - 1) return "Others";
        String ext = name.substring(dot + 1).toLowerCase(Locale.ROOT);
        if (Arrays.asList("jpg","jpeg","png","gif","bmp","webp","svg","heic").contains(ext)) return "Images";
        if (Arrays.asList("mp4","mkv","mov","avi","webm").contains(ext)) return "Videos";
        if (Arrays.asList("mp3","wav","aac","flac","ogg").contains(ext)) return "Audio";
        if (Arrays.asList("pdf","doc","docx","txt","rtf","ppt","pptx","xls","xlsx","csv").contains(ext)) return "Documents";
        if (Arrays.asList("zip","rar","7z","tar","gz").contains(ext)) return "Archives";
        if (Arrays.asList("java","class","jar","py","c","cpp","js","html","css","json","xml").contains(ext)) return "Code";
        return "Others";
    }

    private void executeOrganize() {
        if (pendingMoves.isEmpty()) {
            previewOrganize();
            if (pendingMoves.isEmpty()) return;
        }
        int answer = JOptionPane.showConfirmDialog(this,
                "Move " + pendingMoves.size() + " file(s) into category folders?\nYou can undo this operation.",
                "Confirm organization", JOptionPane.YES_NO_OPTION, JOptionPane.WARNING_MESSAGE);
        if (answer != JOptionPane.YES_OPTION) return;
        executeBatch(new ArrayList<MoveRecord>(pendingMoves), "Organization completed.");
        pendingMoves.clear();
    }

    private void moveDuplicatesToTrash() {
        if (duplicateFiles.isEmpty()) { info("Find duplicates first."); return; }
        int answer = JOptionPane.showConfirmDialog(this,
                "Move all detected duplicate files except the first copy in each group to .trash?\nNothing will be permanently deleted.",
                "Confirm duplicate cleanup", JOptionPane.YES_NO_OPTION, JOptionPane.WARNING_MESSAGE);
        if (answer != JOptionPane.YES_OPTION) return;

        // Keep one copy per identical-content group.
        Map<String, List<Path>> groups = new LinkedHashMap<String, List<Path>>();
        try {
            for (Path p : scannedFiles) {
                if (!Files.exists(p)) continue;
                String key = Files.size(p) + ":" + sha256(p);
                groups.computeIfAbsent(key, k -> new ArrayList<Path>()).add(p);
            }
        } catch (Exception ex) { showError("Could not verify duplicates", ex); return; }

        List<MoveRecord> moves = new ArrayList<MoveRecord>();
        Path trash = selectedFolder.resolve(".trash");
        for (List<Path> group : groups.values()) {
            if (group.size() > 1) {
                for (int i = 1; i < group.size(); i++) {
                    Path source = group.get(i);
                    moves.add(new MoveRecord(source, uniqueTarget(trash.resolve(source.getFileName()))));
                }
            }
        }
        executeBatch(moves, "Duplicate files moved to .trash.");
        duplicateFiles.clear();
        scannedFiles.removeIf(p -> !Files.exists(p));
        showFiles(scannedFiles, Collections.<Path>emptySet(), "Scanned");
    }

    private void executeBatch(List<MoveRecord> moves, String message) {
        List<MoveRecord> completed = new ArrayList<MoveRecord>();
        try {
            for (MoveRecord move : moves) {
                if (!Files.exists(move.original)) continue;
                Files.createDirectories(move.moved.getParent());
                Path actual = uniqueTarget(move.moved);
                Files.move(move.original, actual);
                completed.add(new MoveRecord(move.original, actual));
            }
            if (!completed.isEmpty()) {
                historyUndo.push(new ArrayList<MoveRecord>(completed));
                historyRedo.clear();
            }
            refreshAfterOperation();
            status(message + " Moved: " + completed.size() + ".");
        } catch (Exception ex) {
            // Roll back any moves already completed in this batch.
            Collections.reverse(completed);
            for (MoveRecord r : completed) {
                try { if (Files.exists(r.moved)) Files.move(r.moved, r.original); } catch (IOException ignored) {}
            }
            showError("Operation failed; completed moves were rolled back where possible", ex);
        }
        updateButtons();
    }

    // Batch-based history keeps a whole user action together.
    private final Deque<List<MoveRecord>> historyUndo = new ArrayDeque<List<MoveRecord>>();
    private final Deque<List<MoveRecord>> historyRedo = new ArrayDeque<List<MoveRecord>>();
    private void undo() {
        if (historyUndo.isEmpty()) return;
        List<MoveRecord> batch = historyUndo.peek();
        try {
            for (int i = batch.size() - 1; i >= 0; i--) {
                MoveRecord r = batch.get(i);
                if (Files.exists(r.moved)) {
                    Files.createDirectories(r.original.getParent());
                    Files.move(r.moved, uniqueTarget(r.original));
                }
            }
            historyUndo.pop();
            historyRedo.push(batch);
            refreshAfterOperation();
            status("Last batch undone.");
        } catch (Exception ex) { showError("Undo failed", ex); }
        updateButtons();
    }

    private void redo() {
        if (historyRedo.isEmpty()) return;
        List<MoveRecord> batch = historyRedo.peek();
        List<MoveRecord> completed = new ArrayList<MoveRecord>();
        try {
            for (MoveRecord r : batch) {
                if (Files.exists(r.original)) {
                    Path target = uniqueTarget(r.moved);
                    Files.createDirectories(target.getParent());
                    Files.move(r.original, target);
                    completed.add(new MoveRecord(r.original, target));
                }
            }
            historyRedo.pop();
            historyUndo.push(completed);
            refreshAfterOperation();
            status("Last batch redone.");
        } catch (Exception ex) { showError("Redo failed", ex); }
        updateButtons();
    }

    private Path uniqueTarget(Path desired) {
        if (!Files.exists(desired)) return desired;
        String name = desired.getFileName().toString();
        String stem = name, ext = "";
        int dot = name.lastIndexOf('.');
        if (dot > 0) { stem = name.substring(0, dot); ext = name.substring(dot); }
        int n = 1;
        Path candidate;
        do {
            candidate = desired.resolveSibling(stem + " (" + n++ + ")" + ext);
        } while (Files.exists(candidate));
        return candidate;
    }

    private void refreshAfterOperation() {
        if (selectedFolder == null) return;
        scannedFiles.clear();
        try (Stream<Path> walk = Files.walk(selectedFolder)) {
            walk.filter(Files::isRegularFile)
                .filter(p -> !p.startsWith(selectedFolder.resolve(".trash")))
                .forEach(scannedFiles::add);
        } catch (IOException ex) { status("Refresh warning: " + ex.getMessage()); }
        duplicateFiles.clear();
        showFiles(scannedFiles, duplicateFiles, "Scanned");
    }

    private void showFiles(List<Path> files, Set<Path> duplicates, String normalStatus) {
        model.setRowCount(0);
        for (Path p : files) {
            try {
                BasicFileAttributes a = Files.readAttributes(p, BasicFileAttributes.class);
                String name = p.getFileName().toString();
                String type = extension(name);
                String size = readableSize(a.size());
                String modified = dateFormat.format(new Date(a.lastModifiedTime().toMillis()));
                String state = duplicates.contains(p) ? "DUPLICATE" : normalStatus.toUpperCase(Locale.ROOT);
                model.addRow(new Object[]{name, type, size, modified, selectedFolder.relativize(p).toString(), state});
            } catch (IOException ignored) {}
        }
        countLabel.setText(files.size() + " files");
        updateButtons();
    }

    private String extension(String name) {
        int dot = name.lastIndexOf('.');
        return dot > 0 && dot < name.length() - 1 ? name.substring(dot + 1).toUpperCase(Locale.ROOT) : "—";
    }

    private String readableSize(long bytes) {
        if (bytes < 1024) return bytes + " B";
        double kb = bytes / 1024.0;
        if (kb < 1024) return String.format(Locale.ROOT, "%.1f KB", kb);
        double mb = kb / 1024.0;
        if (mb < 1024) return String.format(Locale.ROOT, "%.1f MB", mb);
        return String.format(Locale.ROOT, "%.2f GB", mb / 1024.0);
    }

    private void clearResults() {
        model.setRowCount(0);
        scannedFiles.clear();
        duplicateFiles.clear();
        pendingMoves.clear();
        countLabel.setText("0 files");
        status("Results cleared. Your files were not changed.");
        updateButtons();
    }

    private boolean ensureFolder() {
        if (selectedFolder == null || !Files.isDirectory(selectedFolder)) {
            info("Choose a valid folder first.");
            return false;
        }
        return true;
    }

    private void setBusy(boolean busy) {
        scanButton.setEnabled(!busy);
        duplicatesButton.setEnabled(!busy && !scannedFiles.isEmpty());
        previewButton.setEnabled(!busy && !scannedFiles.isEmpty());
        organizeButton.setEnabled(!busy && !scannedFiles.isEmpty());
        trashButton.setEnabled(!busy && !duplicateFiles.isEmpty());
        status(busy ? "Working... please wait." : statusLabel.getText());
    }

    private void updateButtons() {
        boolean hasFiles = !scannedFiles.isEmpty();
        duplicatesButton.setEnabled(hasFiles);
        previewButton.setEnabled(hasFiles);
        organizeButton.setEnabled(hasFiles);
        trashButton.setEnabled(!duplicateFiles.isEmpty());
        undoButton.setEnabled(!historyUndo.isEmpty());
        redoButton.setEnabled(!historyRedo.isEmpty());
    }

    private void status(String message) { statusLabel.setText(message); }
    private void info(String message) { JOptionPane.showMessageDialog(this, message, "Smart File Organizer", JOptionPane.INFORMATION_MESSAGE); }
    private void showError(String message, Exception ex) {
        Throwable cause = ex;
        if (ex instanceof java.util.concurrent.ExecutionException && ex.getCause() != null) cause = ex.getCause();
        JOptionPane.showMessageDialog(this, message + ":\n" + cause.getMessage(), "Error", JOptionPane.ERROR_MESSAGE);
        status("An error occurred: " + cause.getMessage());
    }

    public static void main(String[] args) {
        SwingUtilities.invokeLater(() -> {
            try { UIManager.setLookAndFeel(UIManager.getSystemLookAndFeelClassName()); }
            catch (Exception ignored) {}
            new SmartFileOrganizer().setVisible(true);
        });
    }
}
