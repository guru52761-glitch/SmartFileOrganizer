# Smart File Organizer

A desktop file organizer made with **core Java, Swing, and Java NIO**. It has a simple interface while performing real filesystem operations.

## Features

- Select a folder and scan its files recursively.
- Find duplicate files using a two-stage check: file size, then SHA-256 content hash.
- Preview file organization before changing anything.
- Organize files into Images, Videos, Audio, Documents, Archives, Code, and Others.
- Move duplicate copies to a `.trash` folder instead of permanently deleting them.
- Undo and redo the most recent organization/cleanup batches.
- Avoid filename collisions by generating names such as `photo (1).jpg`.
- Run scanning and duplicate detection in a background worker so the interface stays responsive.

## Requirements

- JDK 8 or newer
- No external libraries or build tools required

## Run on Windows

1. Extract the ZIP file.
2. Open the extracted folder in File Explorer.
3. Double-click `run.bat`.

Or open Command Prompt in this folder and run:

```bat
javac SmartFileOrganizer.java
java SmartFileOrganizer
```

## Run on macOS / Linux

```bash
javac SmartFileOrganizer.java
java SmartFileOrganizer
```

## How to use

1. Click **Choose folder** and select a folder you want to manage.
2. Click **Scan folder** to list its files.
3. Click **Find duplicates** to identify identical copies.
4. Click **Preview organize** to see the planned moves. Preview does not modify files.
5. Click **Organize files** and confirm to perform the moves.
6. Use **Undo** or **Redo** to reverse or re-apply the latest batch.
7. After finding duplicates, click **Move duplicates to Trash** to keep one copy and move the other copies into `.trash`.

## Safety notes

- Test the app on a copy of important files first.
- Duplicate cleanup moves files to `.trash`; it does not permanently delete them.
- Undo/redo history is kept in memory while the app is running. It is not persisted after closing the app.
- The current version organizes by file category/extension. Date, size-range, regex rules, background watching, and external plugin JAR loading are suitable next enhancements.

## Project structure

```text
SmartFileOrganizer/
├── SmartFileOrganizer.java
├── run.bat
└── README.md
```

## Design concepts used

- **Strategy-style rule selection:** file categories are resolved by extension.
- **Command-style operations:** each move is recorded with its source and destination, enabling undo/redo.
- **Java NIO:** `Files.walk`, `Files.move`, `Files.createDirectories`, and file attributes.
- **SHA-256:** content-based duplicate detection.
- **SwingWorker:** long-running scans happen off the UI thread.
