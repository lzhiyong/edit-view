package com.text.edit;

import java.io.File;
import java.io.FileReader;
import java.io.IOException;
import java.io.LineNumberReader;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.HashSet;

public class FileUtils {
    // record opened files
    private static HashSet<String> fileLists = new HashSet<>();

    public static void addOpenedFile(String pathname) {
        fileLists.add(pathname);
    }
    
    public static void removeOpenedFile(String pathname){
        if(fileLists.size() > 0)
            fileLists.remove(pathname);
    }

    public static HashSet<String> getOpenedFileList() {
        return fileLists;
    }

    // get the line count of the file
    public static int getLineNumber(File file) {
        try {
            FileReader fileReader = new FileReader(file);
            LineNumberReader lineNumberReader = new LineNumberReader(fileReader);

            lineNumberReader.skip(Integer.MAX_VALUE);
            int lines = lineNumberReader.getLineNumber() + 1;

            fileReader.close();
            lineNumberReader.close();
            // return total lines of the file
            return lines;
        } catch(IOException e) {
            e.printStackTrace();
        }
        return 0;
    }

    public static boolean checkOpenFileState(Path path) {
        if(!Files.exists(path) || !Files.isReadable(path)) 
            return false;
        return true;
    }

    public static boolean checkSaveFileState(Path path) {
        if(!Files.isWritable(path)) 
            return false;
        return true;
    }

    public static boolean checkSameFile(Path path) {
        try {
            for(String pathname:fileLists) {
                if(Files.isSameFile(path, Paths.get(pathname)))
                    return true;
            }
        } catch(IOException e) {
            e.printStackTrace();
        }
        return false;
    }
}
