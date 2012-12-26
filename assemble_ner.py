#!/usr/bin/python
'''
Takes a directory of StreamCorpus.Chunk files as input, puts the
body.cleansed portions into the XML format expected by the
kba-stanford-corenlp wrapper, runs the wrapper, parses the output,
puts it back into the Chunk, and writes the new version of the chunk.

This is designed to run in Condor, which means that it finishes each
step in temp files before doing an atomic move to put the final
product into position.
'''

## assume that StreamCorpus has been installed
import StreamCorpus

import os
import re
import time
import hashlib
import argparse
import traceback
import subprocess

parser = argparse.ArgumentParser()
parser.add_argument('input_dir', help='directory of StreamCorpus.Chunk files')
parser.add_argument('output_dir', help='directory to put new StreamCorpus.Chunk files')
## could add options to delete input after verifying the output on disk?
parser.add_argument('runNER', help='path to runNER.jar')
args = parser.parse_args()

stream_id_re = re.compile('<FILENAME\s+id="(.*?)"')

for fname in os.listdir(args.input_dir):
    fpath = os.path.join(args.input_dir, fname)
    
    ## just need one chunk for this tiny corpus
    i_chunk = StreamCorpus.Chunk(file_obj=open(fpath))

    ## make a temp file for passing cleansed text through NER
    tmp_cleansed_path = os.path.join('/tmp', fname + '.cleansed')
    tmp_cleansed = open(tmp_cleansed_path, 'wb')
    for idx, si in enumerate(i_chunk):
        tmp_cleansed.write('<FILENAME docid="%s">\n' % si.stream_id)
        tmp_cleansed.write(si.body.cleansed)
        tmp_cleansed.write('</FILENAME>\n')
    tmp_cleansed.close()
    print 'created %s' % tmp_cleansed_path

    ## runNER as a child process to get OWPL output
    tmp_ner_path = os.path.join('/tmp', fname + '.ner')
    runNERpath = os.path.join(args.runNER, 'runNER.jar')
    start_time = time.time()
    try:
        gpg_child = subprocess.Popen(
            ['java', '-Xmx2048m', '-jar', runNERpath, tmp_cleansed_path, tmp_ner_path],
            stderr=subprocess.PIPE)
        s_out, errors = gpg_child.communicate()
        assert 'Exception' not in errors, errors
    except Exception, exc:
        print traceback.format_exc(exc)
        print 'failed to create %s' % tmp_ner_path
        continue
    elapsed = time.time() - start_time
    print 'created %s in %.1f sec' % (tmp_ner_path, elapsed)

    all_ner = open(tmp_ner_path)
    o_chunk = StreamCorpus.Chunk()
    input_iter = i_chunk.__iter__()
    ner = ''
    for line in all_ner.readlines():
        if line.startswith('<FILENAME'):

            ## if we are past the first one
            if ner:
                stream_item = input_iter.next()
                ## stream_id was set on an earlier loop
                assert stream_id == stream_item.stream_id, \
                    '%s != %s' % (stream_id, stream_item.stream_id)
                stream_item.body.ner = ner

                ## make a label
                label = StreamCorpus.Label()
                label.target_id = stream_id
                stream_item.body.labels = [label]

                o_chunk.add(stream_item)

                ## reset state machine for next doc
                ner = ''

            ## get the next stream_id
            stream_id = stream_id_re.match(line).group(1)

        elif not line.startswith('</FILENAME>'):
            ner += line + '\n'

    tmp_done_path = os.path.join('/tmp', fname + '.done')
    open(tmp_done_path, 'wb').write(str(o_chunk))
    print 'created %s' % tmp_done_path

    done_path = os.path.join(args.output_dir, fname)
    os.rename(tmp_done_path, done_path)
    print 'done with %s' % done_path
