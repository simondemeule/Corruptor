#!/bin/bash
# Run Corruptor with predefined settings.
# Usage: java <path_to_corruptor> <path_to_input_file> <path_to_output_file> <bit_wise_error_probability> <seed_integer>
java "${BASH_SOURCE%/*}/Corruptor.java" /Users/simondemeule/GitHub/Corruptor/in.jpg /Users/simondemeule/GitHub/Corruptor/out.jpg 0.00001 0