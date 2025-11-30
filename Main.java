//TIP To <b>Run</b> code, press <shortcut actionId="Run"/> or
// click the <icon src="AllIcons.Actions.Execute"/> icon in the gutter.
import java.io.BufferedReader;
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.OutputStream;

public final class Main {

    private static final int WIDTH = 320;
    private static final int HEIGHT = 160;
    private static final int FPS = 60;
    private static final double DURATION_SECONDS = 4.0;
    private static final String OUTPUT_DIR = "frames";
    private static final String OUTPUT_VIDEO = "nether.mp4";

    public static void main(String[] args) {
        try {
            runSafely();
        } catch (Throwable t) {
            System.err.println("[FATAL] Unexpected unrecoverable error: " + t.getMessage());
            t.printStackTrace(System.err);
            System.exit(1);
        }
    }

    private static void runSafely() {
        try {
            File framesDir = prepareOutputDirectory(OUTPUT_DIR);
            renderFrames(framesDir);
            runFfmpeg(framesDir);
            System.out.println("[OK] All frames rendered and video created: " + OUTPUT_VIDEO);
        } catch (IllegalArgumentException e) {
            System.err.println("[CONFIG ERROR] " + e.getMessage());
        } catch (IOException e) {
            System.err.println("[IO ERROR] " + e.getMessage());
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            System.err.println("[INTERRUPTED] Rendering or encoding was interrupted.");
        }
    }
    private static File prepareOutputDirectory(String dirName) throws IOException {
        if (dirName == null || dirName.trim().isEmpty()) {
            throw new IllegalArgumentException("Output directory name must not be null or empty.");
        }

        File dir = new File(dirName);
        if (dir.exists()) {
            if (!dir.isDirectory()) {
                throw new IOException("Output path exists and is not a directory: " + dir.getAbsolutePath());
            }
        } else {
            if (!dir.mkdirs()) {
                throw new IOException("Failed to create output directory: " + dir.getAbsolutePath());
            }
        }

        if (!dir.canWrite()) {
            throw new IOException("Output directory is not writable: " + dir.getAbsolutePath());
        }

        System.out.println("[INFO] Using output directory: " + dir.getAbsolutePath());
        return dir;
    }

    private static void renderFrames(File framesDir) throws IOException, InterruptedException {
        if (framesDir == null) {
            throw new IllegalArgumentException("framesDir must not be null.");
        }

        if (WIDTH <= 0 || HEIGHT <= 0) {
            throw new IllegalArgumentException("Width and height must be positive. Got " + WIDTH + "x" + HEIGHT);
        }

        int totalFrames = (int) (FPS * DURATION_SECONDS);
        System.out.println("[INFO] Rendering " + totalFrames + " frames at " + WIDTH + "x" + HEIGHT + " @ " + FPS + " FPS");

        for (int frameIndex = 0; frameIndex < totalFrames; frameIndex++) {
            if (Thread.currentThread().isInterrupted()) {
                throw new InterruptedException("Rendering interrupted before frame " + frameIndex);
            }

            double time = frameIndex / (double) FPS;
            File outFile = new File(framesDir, String.format("frame_%04d.ppm", frameIndex));

            try {
                writeFramePPM(outFile, time);
            } catch (IOException e) {
                throw new IOException("Failed to write frame " + frameIndex + " to " +
                        outFile.getAbsolutePath() + ": " + e.getMessage(), e);
            }

            if (frameIndex % 10 == 0 || frameIndex == totalFrames - 1) {
                System.out.println("[INFO] Rendered frame " + frameIndex + "/" + (totalFrames - 1));
            }
        }
    }

    private static void writeFramePPM(File file, double time) throws IOException {
        if (file == null) {
            throw new IllegalArgumentException("Target frame file must not be null.");
        }

        String header = "P6\n" + WIDTH + " " + HEIGHT + "\n255\n";

        try (OutputStream os = new FileOutputStream(file)) {
            os.write(header.getBytes());

            for (int y = 0; y < HEIGHT; y++) {
                for (int x = 0; x < WIDTH; x++) {
                    double u = (x + 0.5) / WIDTH;
                    double v = (y + 0.5) / HEIGHT;

                    Vec4 color = shadeNether(u, v, time);

                    int r = toByte(color.x);
                    int g = toByte(color.y);
                    int b = toByte(color.z);

                    os.write(r);
                    os.write(g);
                    os.write(b);
                }
            }
        }
    }

    private static void runFfmpeg(File framesDir) throws IOException, InterruptedException {
        if (framesDir == null) {
            throw new IllegalArgumentException("framesDir must not be null for ffmpeg.");
        }

        File inputPattern = new File(framesDir, "frame_%04d.ppm");

        System.out.println("[INFO] Starting ffmpeg to create video: " + OUTPUT_VIDEO);
        ProcessBuilder pb = new ProcessBuilder(
                "ffmpeg",
                "-y",
                "-framerate", String.valueOf(FPS),
                "-i", inputPattern.getPath(),
                "-c:v", "libx264",
                "-pix_fmt", "yuv420p",
                OUTPUT_VIDEO
        );
        pb.redirectErrorStream(true);

        Process process;
        try {
            process = pb.start();
        } catch (IOException e) {
            throw new IOException(
                    "Failed to start ffmpeg. Ensure ffmpeg is installed and available in your PATH. " +
                            "Original error: " + e.getMessage(), e
            );
        }

        try (BufferedReader reader = new BufferedReader(
                new InputStreamReader(process.getInputStream()))) {
            String line;
            while ((line = reader.readLine()) != null) {
                System.out.println("[FFMPEG] " + line);
            }
        }

        int exitCode = process.waitFor();
        if (exitCode != 0) {
            throw new IOException("ffmpeg exited with non-zero code: " + exitCode +
                    ". Check the ffmpeg logs above for details.");
        }

        System.out.println("[INFO] ffmpeg finished successfully.");
    }

    private static Vec4 shadeNether(double u, double v, double time) {
        Vec3 FC = new Vec3(u, v, 0.0);
        Vec3 r = new Vec3(1.0, 1.0, 1.0);

        Vec4 o = new Vec4(0.0, 0.0, 0.0, 0.0);
        double z = 0.0;
        double d = 0.0;

        for (double i = 0.0; i++ < 60.0; ) {
            Vec3 p = Vec3.normalize(FC.mul(2.0).sub(r.xyy())).mul(z);

            p.z -= time;

            p = p.div(0.1).round().mul(0.1);

            for (d = 0.0; d++ < 9.0; ) {
                Vec3 temp = p.mul(d).add(Vec3.splat(z));
                temp = Vec3.cos(temp);
                Vec3 zzx = new Vec3(temp.z, temp.z, temp.x);
                p = p.add(zzx.mul(0.2));
            }

            d = Math.abs(Math.abs(p.y) - 3.0) / 20.0;
            z += d;

            double inv = 1.0 / (d * d);
            Vec4 add = new Vec4(19.0, z, 1.0, 1.0).mul(inv);
            o = o.add(add);
        }

        o = o.div(700000.0).tanh();
        return o;
    }


    private static int toByte(double v) {
        double c = (v + 1.0) * 0.5;
        if (c < 0.0) c = 0.0;
        if (c > 1.0) c = 1.0;
        return (int) Math.round(c * 255.0);
    }


    private static final class Vec3 {
        double x, y, z;

        Vec3(double x, double y, double z) {
            this.x = x;
            this.y = y;
            this.z = z;
        }

        Vec3 add(Vec3 o) {
            if (o == null) throw new IllegalArgumentException("Vec3.add: other must not be null");
            return new Vec3(x + o.x, y + o.y, z + o.z);
        }

        Vec3 sub(Vec3 o) {
            if (o == null) throw new IllegalArgumentException("Vec3.sub: other must not be null");
            return new Vec3(x - o.x, y - o.y, z - o.z);
        }

        Vec3 mul(double s) {
            return new Vec3(x * s, y * s, z * s);
        }

        Vec3 mul(Vec3 o) {
            if (o == null) throw new IllegalArgumentException("Vec3.mul: other must not be null");
            return new Vec3(x * o.x, y * o.y, z * o.z);
        }

        Vec3 div(double s) {
            if (s == 0.0) throw new IllegalArgumentException("Vec3.div: divisor must not be zero");
            return new Vec3(x / s, y / s, z / s);
        }

        Vec3 round() {
            return new Vec3(Math.rint(x), Math.rint(y), Math.rint(z));
        }

        Vec3 xyy() {
            return new Vec3(x, y, y);
        }

        static Vec3 splat(double v) {
            return new Vec3(v, v, v);
        }

        static Vec3 normalize(Vec3 v) {
            if (v == null) throw new IllegalArgumentException("Vec3.normalize: vector must not be null");
            double len = Math.sqrt(v.x * v.x + v.y * v.y + v.z * v.z);
            if (len == 0.0) {
                return new Vec3(0.0, 0.0, 0.0);
            }
            return v.div(len);
        }

        static Vec3 cos(Vec3 v) {
            if (v == null) throw new IllegalArgumentException("Vec3.cos: vector must not be null");
            return new Vec3(Math.cos(v.x), Math.cos(v.y), Math.cos(v.z));
        }
    }

    private static final class Vec4 {
        double x, y, z, w;

        Vec4(double x, double y, double z, double w) {
            this.x = x;
            this.y = y;
            this.z = z;
            this.w = w;
        }

        Vec4 add(Vec4 o) {
            if (o == null) throw new IllegalArgumentException("Vec4.add: other must not be null");
            return new Vec4(x + o.x, y + o.y, z + o.z, w + o.w);
        }

        Vec4 mul(double s) {
            return new Vec4(x * s, y * s, z * s, w * s);
        }

        Vec4 div(double s) {
            if (s == 0.0) throw new IllegalArgumentException("Vec4.div: divisor must not be zero");
            return new Vec4(x / s, y / s, z / s, w / s);
        }

        Vec4 tanh() {
            return new Vec4(
                    Math.tanh(x),
                    Math.tanh(y),
                    Math.tanh(z),
                    Math.tanh(w)
            );
        }
    }
}
