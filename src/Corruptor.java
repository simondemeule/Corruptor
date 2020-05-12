import java.io.*;
import java.util.Random;
import java.util.Scanner;

// Written by Simon Demeule
// Feed this thing a file and it will break it.
// Adds randomly distributed bit errors to any kind of file.

public class Corruptor {
    private final int BUFFER_SIZE = 4096; // 4KB

    private byte[] buffer = new byte[BUFFER_SIZE];

    private long[] errorsChosenByteInBuffer = new long[BUFFER_SIZE];

    private long[] errorsNumberBitsInByte = new long[9];

    private long[] errorsChosenBitInByte = new long[8];

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

    private void corruptBufferAt(int index, double probability) {
        // this corrupts at least one bit at a given internal buffer location and iteratively attempts to
        // corrupt further bits given a probability

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
            errorsChosenBitInByte[chosenBit]++;
            errorsNumberBitsInByte[1]++;
            errorsChosenByteInBuffer[index]++;
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
            int errorsNumberBits = 0;

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
                errorsNumberBits++;
                errorsChosenBitInByte[chosenBit]++;
                errorsChosenByteInBuffer[index]++;
            }
            // increment error counter according to the number of bit errors in that byte
            errorsNumberBitsInByte[errorsNumberBits]++;
        }
    }

    private void corruptBufferRange(int from, int to, double probability) {
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
            errorsNumberBitsInByte[0] += to - from + 1;
        }
    }

    private void corruptBuffer(double probability) {
        // corrupts the internal buffer
        corruptBufferRange(0, BUFFER_SIZE - 1, probability);
    }

    public void corruptFile(File inputFile, File outputFile, double probability) {
        System.out.println("____________________________________");
        System.out.println("Running");
        // corrupt an entire file
        for(int i = 0; i < errorsChosenByteInBuffer.length; i++) {
            errorsChosenByteInBuffer[i] = 0;
        }
        for(int i = 0; i < errorsNumberBitsInByte.length; i++) {
            errorsNumberBitsInByte[i] = 0;
        }
        for(int i = 0; i < errorsChosenBitInByte.length; i++) {
            errorsChosenBitInByte[i] = 0;
        }
        try (
            InputStream inputStream = new FileInputStream(inputFile);
            OutputStream outputStream = new FileOutputStream(outputFile);
        ) {
            double buffersTotal = inputFile.length() / (BUFFER_SIZE * 1.0);
            int bufferCounter = 0;
            while (inputStream.read(buffer) != -1) {
                // apply buffer corruption over file
                corruptBuffer(probability);
                outputStream.write(buffer);
                // display progress
                bufferCounter++;
                System.out.print("\r" + String.format("%.2f", (bufferCounter / buffersTotal) * 100) + "%");
            }
            System.out.println("\r" + String.format("%.2f", 100.0) + "%");
            inputStream.close();
            outputStream.close();
            System.out.println("____________________________________");
            System.out.println("Done");
        } catch (IOException exception) {
            exception.printStackTrace();
            System.exit(1);
        }
    }

    public void printErrorsPerByte() {
        // print some stats about the way errors are distributed within bytes
        System.out.println("____________________________________");
        System.out.println("Number of bit errors in same byte");
        System.out.println("");
        long totalBitErrors = 0;
        long totalBits = 0;
        for(int i = 0; i < errorsNumberBitsInByte.length; i++) {
            System.out.println(i + "-bit errors: " + errorsNumberBitsInByte[i]);
            totalBitErrors += i * errorsNumberBitsInByte[i];
            totalBits += 8 * errorsNumberBitsInByte[i];
        }
        double probability = totalBitErrors / (1.0 * totalBits);
        System.out.println("____________________________________");
        System.out.println("Total bits corrupted: " + totalBitErrors + " of " + totalBits);
        System.out.println("Measured probability: " + probability);
    }

    public void printErrorsPerBit() {
        // print some stats about which bits were chosen to be corrupted
        System.out.println("____________________________________");
        System.out.println("Bit chosen for error");
        System.out.println("");
        for(int i = 0; i < errorsChosenBitInByte.length; i++) {
            System.out.println("bit-" + i + " errors: " + errorsChosenBitInByte[i]);
        }
    }

    public void printErrorsPerBuffer() {
        // print some stats about which bytes were chosen to be corrupted
        System.out.println("____________________________________");
        System.out.println("Byte chosen for error");
        System.out.println("");
        for(int i = 0; i < errorsChosenByteInBuffer.length; i++) {
            System.out.println("byte-" + i + " errors: " + errorsChosenByteInBuffer[i]);
        }
    }

    public double entropyFile(File inputFile) {
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

    public static void main(String[] arguments) {
        File inputFile = null;
        File outputFile = null;
        double probability = 0;
        int seed = 0;

        if(arguments.length == 0) {
            Scanner scanner = new Scanner(System.in);
            boolean valid;

            System.out.println("____________________________________");
            System.out.println("Input file path (drag and drop)");
            valid = false;
            do {
                String inputFileName = scanner.next();
                inputFile = new File(inputFileName);
                int index = inputFileName.indexOf(".");
                outputFile = new File(inputFileName.substring(0, index + 1) + "corrupt." + inputFileName.substring(index + 1));
                valid = inputFile.exists();
                if(!valid) {
                    System.out.println("____________________________________");
                    System.out.println("Given file does not exist. Try again");
                }
            } while (!valid);

            System.out.println("____________________________________");
            System.out.println("Bitwise corruption probability (0-1)");
            valid = false;
            do {
                if(!scanner.hasNextDouble()) {
                    scanner.next();
                } else {
                    probability = scanner.nextDouble();
                    valid = probability < 1.0 && probability > 0.0;
                }
                if(!valid) {
                    System.out.println("____________________________________");
                    System.out.println("Invalid input. Try again");
                }
            } while (!valid);

            System.out.println("____________________________________");
            System.out.println("Random seed (integer)");
            valid = false;
            do {
                if(!scanner.hasNextInt()) {
                    scanner.next();
                } else {
                    seed = scanner.nextInt();
                    valid = true;
                }
                if(!valid) {
                    System.out.println("____________________________________");
                    System.out.println("Invalid input. Try again");
                }
            } while (!valid);
        } else if (arguments.length == 4) {
            inputFile = new File(arguments[0]);
            outputFile = new File(arguments[1]);
            probability = Double.parseDouble(arguments[2]);
            seed = Integer.parseInt(arguments[3]);
        } else {
            throw new RuntimeException("Invalid arguments given");
        }

        // TODO: add argument handling for command-line operation

        // create the corruptor and give it a seed
        // the seed determines the exact bits that will be corrupted in the file
        // running the program twice with the same settings will yield the same results

        // the nerdy consequence of this and the fact we used bit inversion to create errors
        // is that this program is able to un-corrupt a file by putting in exactly the same parameters

        Corruptor corruptor = new Corruptor(seed);
        corruptor.corruptFile(inputFile, outputFile, probability);
        corruptor.printErrorsPerBit();
        corruptor.printErrorsPerByte();
        System.out.println("");
    }
}