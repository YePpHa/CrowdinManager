import com.google.gson.Gson;
import org.apache.commons.io.output.ByteArrayOutputStream;
import org.apache.commons.io.IOUtils;
import org.apache.commons.lang3.StringEscapeUtils;
import org.apache.http.HttpEntity;
import org.apache.http.HttpResponse;
import org.apache.http.client.fluent.Request;
import org.apache.http.entity.mime.HttpMultipartMode;
import org.apache.http.entity.mime.MultipartEntityBuilder;
import org.apache.http.entity.mime.content.FileBody;
import org.apache.http.util.EntityUtils;

import java.io.*;
import java.net.URL;
import java.net.URLConnection;
import java.nio.charset.Charset;
import java.util.Map;
import java.util.zip.Deflater;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;
import java.util.zip.ZipOutputStream;

/**
 * Created by Jeppe on 24/07/2014.
 */
public class Main {
    public static void main(String[] args) {
        try {
            ArgumentsBuilder builder = new ArgumentsBuilder(args);

            if (builder.hasItem("-m")) {
                String method = builder.getItem("-m");
                if (method.equals("build")) {
                    String key = builder.getItem("--key");
                    String identifier = builder.getItem("--identifier");

                    System.out.println("Using key: " + key);
                    System.out.println("Using identifier: " + identifier);

                    System.out.println("Building Crowdin project...");
                    export(key, identifier);

                    System.out.println("Downloading build...");
                    byte[] zip = download(key, identifier);

                    String includeLang = null;
                    String includeFile = null;

                    if (builder.hasItem("--include") && builder.hasItem("--include-language")) {
                        includeLang = builder.getItem("--include-language");
                        includeFile = builder.getItem("--include");
                    }

                    System.out.println("Building JSON file");
                    byte[] buildFile = build(zip, includeLang, includeFile);

                    if (builder.hasItem("--minify")) {
                        System.out.println("Minifying the JSON file...");
                        Gson gson = new Gson();
                        buildFile = gson.toJson(gson.fromJson(new String(buildFile, Charset.forName("UTF-8")), Map.class)).getBytes(Charset.forName("UTF-8"));
                    }

                    if (builder.hasItem("-o")) {
                        System.out.println("Writing to " + builder.getItem("-o"));
                        File output = new File(builder.getItem("-o"));
                        FileOutputStream fileOutputStream = new FileOutputStream(output);
                        fileOutputStream.write(buildFile, 0, buildFile.length);
                    }
                } else if (method.equals("update")) {
                    String key = builder.getItem("--key");
                    String identifier = builder.getItem("--identifier");

                    System.out.println("Using key: " + key);
                    System.out.println("Using identifier: " + identifier);

                    if (builder.hasItem("--file") && builder.hasItem("--filename")) {
                        System.out.println("Updating file...");
                        update(key, identifier, builder.getItem("--filename"), new File(builder.getItem("--file")));
                    } else {
                        System.out.println("Missing --file and --filename as arguments.");
                    }
                } else {
                    System.out.println("Invalid method!");
                }

            } else {
                System.out.println("No method has been selected!");
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    public static void update(String key, String identifier, String fileName, File file) throws IOException {
        String urlRequest = "https://api.crowdin.net/api/project/" + identifier + "/update-file?key=" + key;

        MultipartEntityBuilder builder = MultipartEntityBuilder.create();
        builder.setMode(HttpMultipartMode.BROWSER_COMPATIBLE);
        builder.addPart("files[" + fileName + "]", new FileBody(file));

        HttpEntity entity = builder.build();

        HttpResponse returnResponse = Request.Post(urlRequest).body(entity).execute().returnResponse();
        String response = EntityUtils.toString(returnResponse.getEntity());
        if (response.indexOf("<success>") != -1) {
            if (response.indexOf("status=\"skipped\"") != -1) {
                System.out.println("Nothing to update.");
            } else {
                System.out.println("Update complete.");
            }
        } else {
            System.out.println("An error occurred while updating file!");
        }
    }

    public static byte[] request(String urlRequest) throws Exception {
        URL url = new URL(urlRequest);
        URLConnection conn = url.openConnection();
        conn.setDoInput(true);
        return IOUtils.toByteArray(conn.getInputStream());
    }

    public static byte[] download(String key, String identifier) throws Exception {
        return request("https://api.crowdin.net/api/project/" + identifier + "/download/all.zip?key=" + key);
    }

    public static byte[] build(byte[] languages, String includeLang, String includeFile) throws Exception {
        InputStream is = new ByteArrayInputStream(languages);

        ZipInputStream stream = new ZipInputStream(is);
        ZipEntry entry = null;

        ByteArrayOutputStream byteArrayOutputStream = new ByteArrayOutputStream();
        byteArrayOutputStream.write("{".getBytes(Charset.forName("UTF-8")));

        byte[] comma = ",\n".getBytes(Charset.forName("UTF-8"));

        boolean first = true;

        if (includeFile != null && includeLang != null) {
            if (!first) {
                byteArrayOutputStream.write(comma);
            }
            first = false;
            byteArrayOutputStream.write(("\"" + StringEscapeUtils.escapeEcmaScript(includeLang) + "\":").getBytes(Charset.forName("UTF-8")));
            byteArrayOutputStream.write(IOUtils.toByteArray(new FileInputStream(includeFile)));
        }

        while((entry = stream.getNextEntry()) != null) {
            if (!entry.isDirectory()) {
                String name = entry.getName();
                String code = name.substring(name.indexOf("language/") + "language/".length(), name.indexOf("/base.json"));

                if (!first) {
                    byteArrayOutputStream.write(comma);
                }
                first = false;
                byteArrayOutputStream.write(("\"" + StringEscapeUtils.escapeEcmaScript(code) + "\":").getBytes(Charset.forName("UTF-8")));
                byteArrayOutputStream.write(IOUtils.toByteArray(stream));
            }
        }

        byteArrayOutputStream.write("}".getBytes(Charset.forName("UTF-8")));

        return byteArrayOutputStream.toByteArray();
    }

    public static void export(String key, String identifier) throws Exception {
        String urlRequest = "https://api.crowdin.net/api/project/" + identifier + "/export?key=" + key;
        byte[] bytes = request(urlRequest);
        String xml = new String(bytes, Charset.forName("UTF-8"));
        if (xml.indexOf("<success status=\"built\"/>") != -1) {
            System.out.println("Build complete.");
        } else if (xml.indexOf("<success status=\"skipped\"/>") != -1) {
            System.out.println("Nothing to build.");
        } else {
            System.out.println("An error occurred while building...");
        }
    }
}
