package de.benediktlang.lrsclient;

import org.apache.commons.vfs2.*;
import org.apache.commons.vfs2.impl.DefaultFileMonitor;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileReader;
import java.io.IOException;
import java.util.LinkedList;
import java.util.List;

public class LogMonitor implements Runnable {
    private File rptFile = null;
    private List<RPTMonitorListener> listeners = new LinkedList<RPTMonitorListener>();

    public LogMonitor(File rptFile) {
        this.rptFile = rptFile;
    }

    public void addListener(RPTMonitorListener listener) {
        listeners.add(listener);
    }

    private static List<String> getNewLines(BufferedReader reader) throws IOException {
        LinkedList<String> newLines = new LinkedList<String>();
        String line = null;
        do {
            line = reader.readLine();
            if (line != null) {
                newLines.add(line);
            }
        } while (line != null);
        return newLines;
    }

    public void startMonitor() throws IOException {
        final FileReader reader = new FileReader(rptFile);
        final BufferedReader buffered = new BufferedReader(reader);
        String line = null;
        do {
            line = buffered.readLine();
        } while (line != null);

        FileSystemManager fsManager = VFS.getManager();
        FileObject listenFile = fsManager.resolveFile(rptFile.getAbsolutePath());


        DefaultFileMonitor fm = new DefaultFileMonitor(new FileListener() {
            @Override
            public void fileCreated(FileChangeEvent fileChangeEvent) throws Exception {

            }

            @Override
            public void fileDeleted(FileChangeEvent fileChangeEvent) throws Exception {

            }

            @Override
            public void fileChanged(FileChangeEvent fileChangeEvent) throws Exception {
                List<String> newLines = getNewLines(buffered);
                for (RPTMonitorListener l : listeners) {
                    l.changed(newLines);
                }

            }
        });
        fm.setRecursive(true);
        fm.addFile(listenFile);
        fm.start();

    }

    @Override
    public void run() {
        try {
            startMonitor();
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    interface RPTMonitorListener {
        public void changed(List<String> newLines);
    }
}
