import java.io.*;
import java.util.Random;

// Written by Simon Demeule
// Feed this thing a file and it will break it.
// Adds randomly distributed bit errors to any kind of file.

public class Corruptor {
    private static final int BUFFER_SIZE = 4096; // 4KB

    private int[] globalBitErrorCounter = {0, 0, 0, 0, 0, 0, 0, 0, 0};

    private int[] globalChosenBitCounter = {0, 0, 0, 0, 0, 0, 0, 0};

    private Random random;

    public Corruptor(int seed) {
        // constructor with seed
        random = new Random(seed);
    }

    private double nonZeroSuccessInTrials(int trials, double probability) {
        // this calculates the probability of no errors occurring in a sequence of <trial> bits each
        // with an independent <probability> of error,

        // in cases where the probability of an error is very low, we can use this test to batch together
        // groups of bits rather than going one by one, vastly reducing processing time.

        // we compare this probability to a random variable situated between 0 and 1.
        // the test succeeds when the random variable is smaller than the calculated probability.

        // if the test succeeds, we must corrupt at least one bit in the group.
        // for the remaining bits, we must recursively apply this test

        // if the test fails, no bit will be corrupted, and we can move on.

        // below is the mathematical derivation of this function

        // B(success, trials) is a bernoulli random process describing a set of independent trials
        // B(success != 0, trials) = 1 - B(success = 0, trials)
        // B(success != 0, trials) = 1 - probability ^ 0 * (1 - probability) ^ trials
        // B(success != 0, trials) = 1 - (1 - probability) ^ trials

        return 1.0 - Math.pow(1.0 - probability, trials);
    }

    /*
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

    public byte[] corruptBuffer(byte[] in, double probability) {
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
                buffer = corruptBuffer(buffer, probability);
                outputStream.write(buffer);
            }
            inputStream.close();
            outputStream.close();

        } catch (IOException ex) {
            ex.printStackTrace();
            System.exit(1);
        }
    }
    */

    private long bufferCounter = 0;

    private byte[] buffer = new byte[BUFFER_SIZE];

    private long[] globalChosenByteCounter = new long[BUFFER_SIZE];

    private String pad(int length) {
        String out = "";
        for(int i = 0; i < length; i++) {
            out = out + "│ ";
        }
        return out;
    }

    public void corruptBufferAt(int index, double probability) {
        // this corrupts at least one bit at a given internal buffer location and iteratively attempts to
        // corrupt further bits given a probability
        globalChosenByteCounter[index]++;
        // given a low probability, the vast majority of bytes will only have single bit errors
        // these don't require a structure for making sure we don't corrupt the same bit twice
        // we can check in advance if we need to corrupt an extra bit and only declare the structure
        // if we actually need it
        if(!(random.nextDouble() < nonZeroSuccessInTrials(7, probability))) {
            // corrupt just one bit, simple
            // choose a bit to corrupt
            int chosenBit = random.nextInt(8);
            // corrupt the byte by inverting the chosen bit
            buffer[index] = (byte) (buffer[index] ^ (1 << chosenBit));
            // increment error counters
            globalChosenBitCounter[chosenBit]++;
            globalBitErrorCounter[1]++;
        } else {
            // corrupt at least two bits, must setup some stuff
            // the set of bits we can choose to corrupt
            int[] uncorruptedBits = {0, 1, 2, 3, 4, 5, 6, 7};
            // the number of bits left to be chosen
            int remainingBits = 8;
            // the number of trials to corrupt individual bits
            int trials = 8;
            // the number of bits that must be corrupted at least
            int mustCorrupt = 2;
            // an error counter
            int localBitErrorCounter = 0;

            while(trials > 0 && (mustCorrupt-- > 0 || random.nextDouble() < nonZeroSuccessInTrials(trials, probability))) {
                // choose a bit to corrupt within those that remain
                int chosenBitIndex = random.nextInt(remainingBits);
                int chosenBit = uncorruptedBits[chosenBitIndex];
                // remove that bit from the remaining choices
                // (swap the last available bit with the one we chose and decrease the number of remaining bits)
                uncorruptedBits[chosenBitIndex] = uncorruptedBits[--remainingBits];
                // corrupt the byte by inverting the chosen bit
                buffer[index] = (byte) (buffer[index] ^ (1 << chosenBit));
                // decrement the number of trials remaining
                trials--;
                // increment error counter
                localBitErrorCounter++;
                globalChosenBitCounter[chosenBit]++;
            }
            // increment error counter according to the number of bit errors in that byte
            globalBitErrorCounter[localBitErrorCounter]++;
        }
    }

    public void corruptBufferRange(int from, int to, double probability) {
        // this corrupts the internal buffer by recursively splitting it at random points when the corruption trial succeeds,
        // similarly to quicksort.
        if(from < to && random.nextDouble() < nonZeroSuccessInTrials((to - from + 1) * 8, probability)) {
            // at least one bit corrupted in range
            int split = random.nextInt(to - from + 1) + from;
            // choose a split point and corrupt at least a bit from that byte
            corruptBufferAt(split, probability);
            // recur on the remaining part of the range
            if(split == from) {
                // special case, split occurred at beginning of range;
                corruptBufferRange(from + 1, to, probability);
            } else if (split == to) {
                // special case, split occurred at end of range
                corruptBufferRange(from, to - 1, probability);
            } else {
                // general case, split occurred at middle of range
                corruptBufferRange(from, split - 1, probability);
                corruptBufferRange(split + 1, to, probability);
            }
        } else if(from == to && random.nextDouble() < nonZeroSuccessInTrials( 8, probability)) {
            // range contains a single byte
            corruptBufferAt(from, probability);
        } else {
            globalBitErrorCounter[0] += to - from + 1;
        }
    }

    public void corruptBuffer(double probability, int division) {
        // corrupts the internal buffer
        int split = 0;
        for(int i = 0; i < division; i++) {
            int temp = split;
            split += BUFFER_SIZE / division;
            if(i == division - 1) {
                corruptBufferRange(temp, BUFFER_SIZE - 1, probability);
            } else {
                corruptBufferRange(temp, split - 1, probability);
            }
        }
        //corruptBufferRange(0, BUFFER_SIZE - 1, probability);
    }

    public void corruptFile(String inputFile, String outputFile, double probability, int division) {
        // corrupt an entire file
        for(int i = 0; i < globalChosenByteCounter.length; i++) {
            globalChosenByteCounter[i] = 0;
        }
        for(int i = 0; i < globalChosenBitCounter.length; i++) {
            globalChosenBitCounter[i] = 0;
        }
        try (
                InputStream inputStream = new FileInputStream(inputFile);
                OutputStream outputStream = new FileOutputStream(outputFile);
        ) {
            bufferCounter = 0;
            while (inputStream.read(buffer) != -1) {
                // apply buffer corruption over file
                corruptBuffer(probability, division);
                bufferCounter++;
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
        long totalBitErrors = 0;
        long totalBits = 0;
        for(int i = 0; i < globalBitErrorCounter.length; i++) {
            System.out.println(i + "-bit errors: " + globalBitErrorCounter[i]);
            totalBitErrors += i * globalBitErrorCounter[i];
            totalBits += 8 * globalBitErrorCounter[i];
        }
        double probability = totalBitErrors / (1.0 * totalBits);
        System.out.println("____________________________________");
        System.out.println("total bits corrupted: " + totalBitErrors + " of " + totalBits);
        System.out.println("measured probability: " + probability);
    }

    public void printChosenBits() {
        // print some stats about which bits were chosen to be corrupted
        System.out.println("____________________________________");
        System.out.println("bit chosen for error");
        for(int i = 0; i < globalChosenBitCounter.length; i++) {
            System.out.println("bit-" + i + " errors: " + globalChosenBitCounter[i]);
        }
    }

    public void printChosenBytes() {
        // print some stats about which bytes were chosen to be corrupted
        System.out.println("____________________________________");
        System.out.println("byte chosen for error");
        for(int i = 0; i < globalChosenByteCounter.length; i++) {
            System.out.println("byte-" + i + " errors: " + globalChosenByteCounter[i]);
        }
    }

    public double entropyFile(String inputFile) {
        // calculate a file's entropy
        try (
                InputStream inputStream = new FileInputStream(inputFile);
        ) {
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

    // something is wrong with probabilities.
    //
    // given    -> measured
    // 0.1      -> 0.18
    // 0.01     -> 0.022
    // 0.001    -> 0.0023
    // 0.0001   -> 0.00022
    // 0.00001  -> 0.000011
    // 0.000001 -> 0.0000011
    //
    // this effect is reduced by subdividing buffers.
    // this effect is not increased further past a certain buffer size.
    // this effect seems independent of file size.
    //
    // maybe this just has to do with numerical accuracy of pow and random.
    // -> unlikely. verified with BigDecimal nonZeroSuccessesInTrials
    //
    // maybe this is a subtle error in recursion that compounds over higher probabilities.
    // -> unlikely. all edge cases have been thoroughly verified by observing the call hierarchy

    public static void main(String[] args) {
        // create the corruptor and give it a seed
        // the seed determines the exact bits that will be corrupted in the file
        // running the program twice with the same settings will yield the same results

        // the nerdy consequence of this and the fact we used bit inversion to create errors
        // is that this program is able to un-corrupt a file by putting in exactly the same parameters

        Corruptor corruptor = new Corruptor(0);

        // this is just setup so that all input files are named in.<typeFile> and out.<typeFile>
        String typeFile = "jpg";
        // set the probability of error for each bit
        double probability = 0.0001;
        // subdivision of buffers
        int division = 1;
        // do the corruption!
        System.out.println("____________________________________");
        System.out.println("running");
        corruptor.corruptFile("in." + typeFile, "out." + typeFile, probability, division);
        System.out.println("____________________________________");
        System.out.println("done");
        // some sweet statistics
        //corruptor.printChosenBytes();
        corruptor.printChosenBits();
        corruptor.printErrors();

        /*
        // Calculate Shannon's entropy on a file, byte-wise
        System.out.println("entropy: " + corruptor.entropyFile("in.jpg"));
        */
    }
}