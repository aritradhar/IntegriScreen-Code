import argparse
import json

import numpy as np

WORDS = np.loadtxt('dictionary', dtype=str)
FONT_POOL = [
    '"Arial", sans-serif',
    '"Helvetica", sans-serif',
    '"Verdana", sans-serif',
    '"Times", serif'
]
ELEMENT_POOL = [
    "textarea",
    "textfield",
    "checkbox",
    "label"
]


def random_word(x_space):
    max_len = 4 if x_space < 3 else 8 if x_space < 6 else 12
    word = np.random.choice(WORDS)
    while len(word) > max_len:
        word = np.random.choice(WORDS)
    return word


def generate_form(title, num_elements=5, fontsize=10, font='"Arial, sans-serif', randomfont=False):

    ratio = [np.random.randint(10, 15), np.random.randint(8, 15)]
    height = np.random.randint(50, 90)
    if height < 70 and ratio[0] < ratio[1]:
        ratio[0] = ratio[1]

    form = {
        "ratio": "{}:{}".format(*ratio),
        "vspace": "{}".format(height),
        "page_id": title,
        "border_thickness": "1",
        "font_family": font,
        "letter_spacing": "normal",
        "form_action": "/IntegriScreenServer/MainServer",
        "elements": [
            {
                "id": "form_title",
                "type": "title",
                "initialvalue": "Random " + title,
                "editable": "false",
                "ulc_x": "5",
                "ulc_y": "5",
                "width": "50",
                "height": "5"
            },
        ]
    }

    elements_start_pos = sorted([x if x % 10 != 9 else x - 1 for x in np.random.choice(range(90), num_elements, replace=False)])
    button_elem = np.random.choice(range(num_elements))

    elements_coords = [(x, min(x + np.random.randint(2, 10), 10 * (x / 10 + 1), elements_start_pos[_i+1] if _i < len(elements_start_pos) - 1 else 90)) for _i, x in enumerate(elements_start_pos)]

    for _i, (start, end) in zip(range(num_elements), elements_coords):
        elem_type = np.random.choice(ELEMENT_POOL) if _i != button_elem else "button"

        form['elements'].append({
            "id": "{}".format(_i),
            "type": elem_type,
            "initialvalue": random_word(end - start) if elem_type != 'checkbox' else "",
            "editable": "true" if elem_type in ["textarea", "textfield", "checkbox"] else "false",
            "ulc_x": (start % 10) * 10 + 1.8,
            "ulc_y": (start / 10) * 10 + 9,
            "width": (end - start) * 10 - 3,  # ghetto margin
            "height": 9
        })

    # save stuff to disk
    for elem in form['elements']:
        # Thank you Java
        elem['ulc_x'] = str(elem['ulc_x'])
        elem['ulc_y'] = str(elem['ulc_y'])
        elem['width'] = str(elem['width'])
        elem['height'] = str(elem['height'])

    with open(title + '.json', 'w') as f:
        json.dump(form, f, indent=2)


if __name__ == '__main__':
    parser = argparse.ArgumentParser()
    parser.add_argument('title')
    parser.add_argument('-n', '--nelems', type=int, default=5)
    parser.add_argument('-d', '--density', type=int, default=1)
    parser.add_argument('-fs', '--fontsize', type=int, default=10)
    parser.add_argument('-ft', '--font', type=str, default='"Arial", sans-serif')
    parser.add_argument('--randomfont', action='store_true')

    args = parser.parse_args()

    font = args.font if args.randomfont else np.random.choice(FONT_POOL)

    generate_form(args.title, args.nelems, args.fontsize, font, args.randomfont)
