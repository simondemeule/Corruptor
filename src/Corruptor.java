import java.io.*;
import java.util.Random;

// Written by Simon Demeule
// Feed this thing a file and it will break it.
// Adds randomly distributed bit errors to any kind of file.

public class Corruptor {
    private static final int BUFFER_SIZE = 4096; // 4KB

    private static int[] globalBitErrorCounter = {0, 0, 0, 0, 0, 0, 0, 0, 0};

    private static int[] globalChosenBitCounter = {0, 0, 0, 0, 0, 0, 0, 0};

    private Random random;

    public Corruptor(int seed) {
        // constructor with seed
        random = new Random(seed);
    }

    private double nonZeroSuccessInTrials(int trials, double probability) {
        // fancy statistics stuff so that we can save some time by applying the randomness at the byte
        // level rather than the bit level, all while retaining the same random distribution.

        // B(success, trials) is a bernoulli random process describing a set of independent trials
        // B(success != 0, trials) = 1 - B(success = 0, trials)
        // B(success != 0, trials) = 1 - probability ^ 0 * (1 - probability) ^ trials
        // B(success != 0, trials) = 1 - (1 - probability) ^ trials

        return 1.0 - Math.pow(1.0 - probability, trials);
    }

    public byte corruptByte(byte in, double probability) {
        // corrupt a byte
        // the set of bits we can choose to corrupt
        int[] uncorruptedBits = {0, 1, 2, 3, 4, 5, 6, 7};
        // the number of bits left to be chosen
        int remainingBits = 8;
        // the number of trials to corrupt individual bits
        int trials = 8;
        // an error counter
        int localBitErrorCounter = 0;
        // if the random variable tells us there is at least one error in the set of remaining trials
        if(random.nextDouble() < nonZeroSuccessInTrials(trials, probability)) {
            do {
                // choose a bit to corrupt within those that remain
                int chosenBitIndex = random.nextInt(remainingBits);
                int chosenBit = uncorruptedBits[chosenBitIndex];
                // remove that bit from the remaining choices
                // (swap the last available bit with the one we chose and decrease the number of remaining bits)
                uncorruptedBits[chosenBitIndex] = uncorruptedBits[--remainingBits];
                // corrupt the input byte by inverting the chosen bit
                in = (byte) (in ^ (1 << chosenBit));
                // decrement the number of trials remaining
                trials--;
                // increment error counter
                localBitErrorCounter++;
                globalChosenBitCounter[chosenBit]++;
            } while (trials > 0 && random.nextDouble() < nonZeroSuccessInTrials(trials, probability) );
            // repeat until we run out of trials or out of luck
        }
        // increment error counter according to the number of bit errors in that byte
        globalBitErrorCounter[localBitErrorCounter]++;
        return in;
    }

    public byte[] corruptByteBuffer(byte[] in, double probability) {
        // corrupt a buffer of bytes
        byte[] out = new byte[in.length];
        for(int i = 0; i < out.length; i++) {
            // apply byte corruption over buffer
            out[i] = corruptByte(in[i], probability);
        }
        return out;
    }

    public void corruptFile(String inputFile, String outputFile, double probability) {
        // corrupt an entire file
        try (
                InputStream inputStream = new FileInputStream(inputFile);
                OutputStream outputStream = new FileOutputStream(outputFile);
        ) {
            byte[] buffer = new byte[BUFFER_SIZE];

            while (inputStream.read(buffer) != -1) {
                // apply buffer corruption over file
                buffer = corruptByteBuffer(buffer, probability);
                outputStream.write(buffer);
            }
            inputStream.close();
            outputStream.close();

        } catch (IOException ex) {
            ex.printStackTrace();
            System.exit(1);
        }
    }

    public void printErrors() {
        // print some stats about the way errors are distributed within bytes
        System.out.println("____________________________________");
        System.out.println("number of bit errors in same byte");
        for(int i = 0; i < globalBitErrorCounter.length; i++) {
            System.out.println(i + "-bit errors: " + globalBitErrorCounter[i]);
        }
    }

    public void printChosenBits() {
        // print some stats about which bits were chosen to be corrupted
        System.out.println("____________________________________");
        System.out.println("bit chosen for error");
        for(int i = 0; i < globalChosenBitCounter.length; i++) {
            System.out.println("bit-" + i + " errors: " + globalChosenBitCounter[i]);
        }
    }

    public double entropyByteBuffer(byte[] in) {
        // calculate a buffer's entropy.
        // can't be used to calculate a file's entropy by splitting it into buffers because entropy isn't linear
        int[] byteOccurence = new int[256];
        for(int i = 0; i < byteOccurence.length; i++) {
            byteOccurence[i] = 0;
        }
        for(int i = 0; i < in.length; i++) {
            byteOccurence[in[i] + 128]++;
        }
        double entropy = 0;
        for(int i = 0; i < byteOccurence.length; i++) {
            if(byteOccurence[i] != 0) {
                // avoid log(0)
                double probability = (byteOccurence[i] * 1.0) / (in.length * 1.0);
                entropy -= probability * Math.log(probability);
            }
        }
        return entropy;
    }

    public double entropyFile(String inputFile) {
        // calculate a file's entropy
        try (
                InputStream inputStream = new FileInputStream(inputFile);
        ) {
            byte[] buffer = new byte[BUFFER_SIZE];
            long byteCount = 0;
            long[] byteOccurence = new long[256];
            for(int i = 0; i < byteOccurence.length; i++) {
                byteOccurence[i] = 0;
            }

            while (inputStream.read(buffer) != -1) {
                for(int i = 0; i < buffer.length; i++) {
                    byteOccurence[buffer[i] + 128]++;
                    byteCount++;
                }
            }
            inputStream.close();

            double entropy = 0;
            for(int i = 0; i < byteOccurence.length; i++) {
                if(byteOccurence[i] != 0) {
                    // avoid log(0)
                    double probability = (byteOccurence[i] * 1.0) / (byteCount * 1.0);
                    entropy -= probability * Math.log(probability) / Math.log(2.0);
                }
            }
            return entropy;

        } catch (IOException ex) {
            ex.printStackTrace();
            System.exit(1);
            return 0;
        }
    }

    public static void main(String[] args) {
        // create the corruptor and give it a seed
        // the seed determines the exact bits that will be corrupted in the file
        // running the program twice with the same settings will yeild the same results

        // the nerdy consequence of this and the fact we used bit inversion to create errors
        // is that this program is able to un-corrupt a file by putting in exacly the same parameters

        Corruptor corruptor = new Corruptor(0);

        // this is just setup so that all input files are named in.<typeFile> and out.<typeFile>
        String typeFile = "jpg";
        // set the probability of error for each bit
        double probability = 1.0 / 100000.0;
        // do the corruption!
        System.out.println("____________________________________");
        System.out.println("running");
        corruptor.corruptFile("in." + typeFile, "out." + typeFile, probability);
        System.out.println("____________________________________");
        System.out.println("done");
        // some sweet statistics
        corruptor.printChosenBits();
        corruptor.printErrors();

        /*
        // Calculate Shannon's entropy on a file, byte-wise
        System.out.println("entropy: " + corruptor.entropyFile("in.jpg"));
        */
    }
}