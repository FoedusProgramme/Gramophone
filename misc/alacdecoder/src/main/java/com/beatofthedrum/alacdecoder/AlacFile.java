/*
 ** AlacFile.java
 **
 ** Copyright (c) 2011-2014 Peter McQuillan
 **
 ** Based on the ALAC decoder - Copyright (c) 2005 David Hammerton
 **
 ** All rights reserved.
 **
 ** Redistribution and use in source and binary forms, with or without
 ** modification, are permitted provided that the following conditions are met:
 **
 **     * Redistributions of source code must retain the above copyright notice,
 **       this list of conditions and the following disclaimer.
 **     * Redistributions in binary form must reproduce the above copyright notice,
 **       this list of conditions and the following disclaimer in the
 **       documentation and/or other materials provided with the distribution.
 **     * The name of the author may not be used to endorse or promote products
 **       derived from this software without specific prior written permission.
 **
 ** THIS SOFTWARE IS PROVIDED BY THE COPYRIGHT HOLDERS AND CONTRIBUTORS "AS IS"
 ** AND ANY EXPRESS OR IMPLIED WARRANTIES, INCLUDING, BUT NOT LIMITED TO, THE
 ** IMPLIED WARRANTIES OF MERCHANTABILITY AND FITNESS FOR A PARTICULAR PURPOSE
 ** ARE DISCLAIMED. IN NO EVENT SHALL THE REGENTS OR CONTRIBUTORS BE LIABLE FOR
 ** ANY DIRECT, INDIRECT, INCIDENTAL, SPECIAL, EXEMPLARY, OR CONSEQUENTIAL
 ** DAMAGES (INCLUDING, BUT NOT LIMITED TO, PROCUREMENT OF SUBSTITUTE GOODS OR
 ** SERVICES; LOSS OF USE, DATA, OR PROFITS; OR BUSINESS INTERRUPTION) HOWEVER
 ** CAUSED AND ON ANY THEORY OF LIABILITY, WHETHER IN CONTRACT, STRICT LIABILITY,
 ** OR TORT (INCLUDING NEGLIGENCE OR OTHERWISE) ARISING IN ANY WAY OUT OF THE USE
 ** OF THIS SOFTWARE, EVEN IF ADVISED OF THE POSSIBILITY OF SUCH DAMAGE.
 **
 */

package com.beatofthedrum.alacdecoder;

public class AlacFile {

    final int buffer_size = 16384;
    public byte[] input_buffer;
    public int[] channel_map;
    public int numchannels = 0;
    public int bytespersample_output = 0;
    /* stuff from setinfo */
    public int setinfo_max_samples_per_frame = 0; // 0x1000 = 4096
    public int max_frame_bytes = 0;
    int ibIdx = 0;
    int input_buffer_bitaccumulator = 0; /* used so we can do arbitrary
						bit reads */
    LeadingZeros lz = new LeadingZeros();
    /* buffers */
    int[][] outputsamples_buffer;
    int[] uncompressed_bytes_buffer_a = null;
    int[] uncompressed_bytes_buffer_b = null;
    int bitspersample_input = 0; // 0x10
    int setinfo_rice_historymult = 0; // 0x28
    int setinfo_rice_initialhistory = 0; // 0x0a
    int setinfo_rice_kmodifier = 0; // 0x0e
    /* end setinfo stuff */
    int[] predictor_coef_table_a = new int[1024];
    int[] predictor_coef_table_b = null;
}