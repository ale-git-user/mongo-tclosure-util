package com.termmed.util;

import com.mongodb.BasicDBObject;
import org.bson.BasicBSONEncoder;
import org.bson.Document;

import java.io.*;

public class BsonGenerator {
    OutputStream os;
    BasicBSONEncoder encoder;

    public BsonGenerator(String filename) throws UnsupportedEncodingException, FileNotFoundException {
        encoder = new BasicBSONEncoder();
        os = getWriter(new File(filename));
    }


    public static OutputStream getWriter(File outFile) throws UnsupportedEncodingException, FileNotFoundException {
        int bufferSize = 16384; // 16KB buffer size
//        int bufferSize = 32768; // 32KB buffer size

        OutputStream outputStream
                = new BufferedOutputStream(new FileOutputStream(outFile), bufferSize);
        return outputStream;
    }

    public void generate(Document document) throws IOException {

        os.write(encoder.encode(new BasicDBObject(document)));
    }

    public void close() throws IOException {
        os.flush();
        os.close();
    }
}
