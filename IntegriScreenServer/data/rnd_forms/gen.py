import argparse
import numpy as np

parser = argparse.ArgumentParser()
parser.add_argument('title')
parser.add_argument('-n', '--nelems', type=int, default=10)
parser.add_argument('-d', '--density', type=int, default=1)
parser.add_argument('-fs', '--fontsize', type=int, default=10)
parser.add_argument('-ft', '--font', type=str, default='"Arial", sans-serif')
parser.add_argument('--randomfont', action='store_true')

args = parser.parse_args()


def random_word():
    pass


def element_fits(corner1, corner2):
    for elem in form['elements']:
        if corner1[0] > elem['ulc_x'] + elem['width'] or corner2[0] > elem['ulc_x']:
            continue  # no overlap
        if corner1[1] < elem['ulc_y'] + elem['height'] or corner2[1] < elem['ulc_y']:
            continue  # no overlap
        # Overlap
        return False
    # No overlap with any element
    return True


font_pool = [
    '"Arial", sans-serif',
    '"Helvetica", sans-serif',
    '"Verdana", sans-serif',
    '"Times", serif'
]

element_pool = [
    "textarea",
    "textfield",
    "checkbox",
    "label",
    "button"
]

font = args.font if args.randomfont else np.random.choice(font_pool)

form = {
    "ratio": "{}:{}".format(*np.random.randint(5, 20, 2)),
    "vspace": "{}".format(np.random.randint(30, 90)),
    "page_id": args.title,
    "border_thickness": "1",
    "font_family": font,
    "letter_spacing": "normal",
    "form_action": "/IntegriScreenServer/MainServer",
    "elements": [
        {
            "id": "form_title",
            "type": "title",
            "initialvalue": args.title,
            "editable": "false",
            "ulc_x": "5",
            "ulc_y": "5",
            "width": "50",
            "height": "6"
        },
    ]
}

for _i in range(args.nelems):
    elem_type = np.random.choice(element_pool)
    fits = False
    corner, size = [], []
    while not fits:
        # calculate x and y space usage of margin+elem
        space = np.array((np.random.randint(10, 95), np.random.randint(20, 80)))
        # draw random margin within the bounds above - 5 => min elem size in both dimensions is 5%
        corner = np.array((np.random.randint(1, space[0] - 5), np.random.randint(10, space[1] - 5)))
        # Get size by difference, this way we always generate valid elements that fit the green border
        size = space - corner
        if element_fits(corner, space):
            fits = True

    form['elements'].append({
        "id": "{}".format(_i),
        "type": elem_type,
        "initialvalue": random_word() if elem_type != 'checkbox' else "",
        "editable": "true",
        "ulc_x": corner[0],
        "ulc_y": corner[1],
        "width": size[0],
        "height": size[1]
    })

# compute crammedness
# append crammedness to json
# save stuff to disk
