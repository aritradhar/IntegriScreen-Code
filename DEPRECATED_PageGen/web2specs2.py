#! /usr/bin/python2

import argparse
import json
import numpy as np

BORDER_SIZE = 3

parser = argparse.ArgumentParser()
parser.add_argument('file', type=str)

args = parser.parse_args()

with open(args.file) as fl:
    specs = json.load(fl)
    
    (x_prop, y_prop) = map(lambda x: float(x), specs['ratio'].split(':'))
    
    vspace = float(specs['vspace'])
    # vert_mul = 100.0 / (vspace + 2 * BORDER_SIZE)
    vert_mul = 100.0 / vspace
    # y_border_offset = 100 * BORDER_SIZE / (vspace + 2 * BORDER_SIZE)
    
    
    hspace = vspace / y_prop * x_prop
    hor_mul = 100.0 / (100.0 + 2 * BORDER_SIZE)
    x_border_offset = 100 * BORDER_SIZE / (hspace + 2 * BORDER_SIZE)
    
    for elem in specs['elements']:
        # Convert vspace and height from vh to percentage relative to the OUTER green border
        elem['ulc_y'] = float(elem['ulc_y']) * vert_mul + y_border_offset
        elem['height'] = float(elem['height']) * vert_mul + y_border_offset
        
        # For hspace from the OUTER green border we just need to add the proper factor
        elem['ulc_x'] = float(elem['ulc_x']) * hor_mul + x_border_offset
        elem['width'] = float(elem['width']) * hor_mul + x_border_offset
    
    with open(''.join(args.file.split('.')[:-1]) + '_specs.json', 'w') as tar:
        json.dump(specs, tar, indent=4)
