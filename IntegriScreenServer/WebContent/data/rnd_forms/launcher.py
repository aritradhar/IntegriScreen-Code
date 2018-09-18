import argparse

from gen import generate_form

parser = argparse.ArgumentParser()
parser.add_argument('how_many_forms', type=int)
parser.add_argument('min_elem_num', type=int)
parser.add_argument('max_elem_num', type=int)

args = parser.parse_args()

forms_per_group = args.how_many_forms / (args.max_elem_num - args.min_elem_num + 1)
generated_forms = 0
for _i in range(args.min_elem_num, args.max_elem_num + 1):
    for _c in range(forms_per_group):
        generate_form("Random_{}".format(generated_forms), num_elements=_i)
        generated_forms += 1

print "No of forms generated: " + str(generated_forms)
