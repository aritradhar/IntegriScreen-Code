#! /usr/bin/python2

import argparse
import json
import numpy as np

parser = argparse.ArgumentParser()
parser.add_argument('file', type=str)

args = parser.parse_args()

with open(args.file) as fl:
    specs = json.load(fl)
    
    vert_mul = 100.0 / float(specs['vspace'])
    
    for elem in specs['elements']:
        # Convert vspace and height from vh to percentage relative to the green border
        elem['ulc_y'] = float(elem['ulc_y']) * vert_mul
        elem['height'] = float(elem['height']) * vert_mul
        
    
    with open(''.join(args.file.split('.')[:-1]) + '_specs.json', 'w') as tar:
        json.dump(specs, tar, indent=4)
