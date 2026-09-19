package jagent.json;

import java.io.BufferedReader;
import java.io.Closeable;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;

public final class Sse implements Closeable {

    public static final String DONE = "[DONE]";

    private final BufferedReader r;

    public Sse(InputStream in) {
        this.r = new BufferedReader(new InputStreamReader(in, StandardCharsets.UTF_8));
    }

    public String next() throws IOException {
        String line;
        while ((line = r.readLine()) != null) {
            if (line.isEmpty() || line.startsWith(":")) continue;
            if (!line.startsWith("data:")) continue;
            String d = line.substring(5);
            if (d.startsWith(" ")) d = d.substring(1);
            return d;
        }
        return null;
    }

    @Override
    public void close() throws IOException {
        r.close();
    }
}
