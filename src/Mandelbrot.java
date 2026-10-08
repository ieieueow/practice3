import java.awt.image.BufferedImage;
import java.io.File;
import java.io.IOException;
import java.util.Arrays;
import java.util.concurrent.atomic.AtomicInteger;
import javax.imageio.ImageIO;

public class Mandelbrot {

    static final int WIDTH = 1600;
    static final int HEIGHT = 1200;
    static final int MAX_ITER = 2000;

    static final double X_MIN = -2.2;
    static final double X_MAX = 1.0;
    static final double Y_MIN = -1.2;
    static final double Y_MAX = 1.2;

    static int iterations(double cr, double ci) {
        double zr = 0, zi = 0;
        int n = 0;
        while (n < MAX_ITER && zr * zr + zi * zi <= 4.0) {
            double tmp = zr * zr - zi * zi + cr;
            zi = 2 * zr * zi + ci;
            zr = tmp;
            n++;
        }
        return n;
    }

    static void computeRow(int y, int[] result) {
        double ci = Y_MIN + (Y_MAX - Y_MIN) * y / (HEIGHT - 1);
        for (int x = 0; x < WIDTH; x++) {
            double cr = X_MIN + (X_MAX - X_MIN) * x / (WIDTH - 1);
            result[y * WIDTH + x] = iterations(cr, ci);
        }
    }

    static boolean inSet(int[] result, int x, int y) {
        return result[y * WIDTH + x] == MAX_ITER;
    }

    static int[] computeSequential() {
        int[] result = new int[WIDTH * HEIGHT];
        for (int y = 0; y < HEIGHT; y++)
            computeRow(y, result);
        return result;
    }

    static int[] computeStatic(int threadCount) throws InterruptedException {
        int[] result = new int[WIDTH * HEIGHT];
        Thread[] threads = new Thread[threadCount];
        int rowsPerThread = (HEIGHT + threadCount - 1) / threadCount;

        for (int i = 0; i < threadCount; i++) {
            int from = i * rowsPerThread;
            int to = Math.min(HEIGHT, from + rowsPerThread);
            threads[i] = new Thread(() -> {
                for (int y = from; y < to; y++)
                    computeRow(y, result);
            });
            threads[i].start();
        }
        for (Thread t : threads)
            t.join();
        return result;
    }

    static int[] computeDynamic(int threadCount) throws InterruptedException {
        int[] result = new int[WIDTH * HEIGHT];
        AtomicInteger nextRow = new AtomicInteger(0);
        Thread[] threads = new Thread[threadCount];

        for (int i = 0; i < threadCount; i++) {
            threads[i] = new Thread(() -> {
                int y;
                while ((y = nextRow.getAndIncrement()) < HEIGHT)
                    computeRow(y, result);
            });
            threads[i].start();
        }
        for (Thread t : threads)
            t.join();
        return result;
    }

    static void savePng(int[] result, String fileName) throws IOException {
        BufferedImage image = new BufferedImage(WIDTH, HEIGHT, BufferedImage.TYPE_INT_RGB);
        for (int y = 0; y < HEIGHT; y++) {
            for (int x = 0; x < WIDTH; x++) {
                int rgb = inSet(result, x, y) ? 0x000000 : 0xFFFFFF;
                image.setRGB(x, y, rgb);
            }
        }
        ImageIO.write(image, "png", new File(fileName));
    }

    static long countInSet(int[] result) {
        long count = 0;
        for (int y = 0; y < HEIGHT; y++)
            for (int x = 0; x < WIDTH; x++)
                if (inSet(result, x, y))
                    count++;
        return count;
    }

    interface Computation {
        int[] run() throws InterruptedException;
    }

    static int[] measure(String name, Computation computation) throws InterruptedException {
        long start = System.nanoTime();
        int[] result = computation.run();
        long time = (System.nanoTime() - start) / 1_000_000;
        System.out.println(name + ": " + time + " ms");
        return result;
    }

    public static void main(String[] args) throws Exception {
        int cores = Runtime.getRuntime().availableProcessors();
        System.out.println("Размер: " + WIDTH + "x" + HEIGHT + ", MAX_ITER = " + MAX_ITER
                + ", ядер: " + cores);

        computeSequential();

        int[] sequential = measure("1 поток", Mandelbrot::computeSequential);
        int[] staticResult = measure(cores + " потоков, куски строк", () -> computeStatic(cores));
        int[] dynamicResult = measure(cores + " потоков, строки по очереди", () -> computeDynamic(cores));

        System.out.println("Результаты совпадают: "
                + (Arrays.equals(sequential, staticResult) && Arrays.equals(sequential, dynamicResult)));
        System.out.println("Пикселей в множестве: " + countInSet(dynamicResult)
                + " из " + (WIDTH * HEIGHT));

        savePng(dynamicResult, "mandelbrot.png");
    }
}