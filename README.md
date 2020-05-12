# Corruptor
A simple file corruptor

# Getting started
Clone or download this repository. 

On macOS or UNIX, go to /src/, and execute "Corruptor Interactive.command" and follow instructions in the command prompt or edit and execute "Corruptor Preset.command" to use with preset arguments.

On Windows, go to /src/ and execute Corruptor.java with no arguments for interactive mode, or with arguments <path_to_corruptor> <path_to_input_file> <path_to_output_file> <bit_wise_error_probability> <seed_integer> for preset execution.

# More about arguments

Bitwise corruption probability is the probability that a single bit will get corrupted (inverted), on a scale from 0 to 1 (0 to 100%).

The random seed is a whole number deterministically decides the random distribution of the errors.

# Interactive Mode Screenshot

![Screenshot](/screenshot.png)
