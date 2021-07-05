package com.termmed.util;

import java.io.*;

public class MetadataGenerator {
    BufferedWriter bw;
    public MetadataGenerator(String filename) throws UnsupportedEncodingException, FileNotFoundException {
        bw = getWriter(filename);

    }

    public void generate(String metadata) throws IOException {
        bw.append(metadata);
    }

    private BufferedWriter getWriter(String outFile) throws UnsupportedEncodingException, FileNotFoundException {

        FileOutputStream tfos = new FileOutputStream( outFile);
        OutputStreamWriter tfosw = new OutputStreamWriter(tfos,"UTF-8");
        return new BufferedWriter(tfosw);

    }

    public void close() throws IOException {
        bw.close();
    }
}
