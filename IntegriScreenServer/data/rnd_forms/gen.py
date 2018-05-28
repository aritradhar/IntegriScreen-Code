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
    # "checkbox",
    "label"
]


def random_word(x_space):
    max_len = 4 if x_space < 3 else 8 if x_space < 6 else 12
    word = np.random.choice(WORDS)
    while len(word) > max_len:
        word = np.random.choice(WORDS)
    return word


def find_max_end(x, _i, next_element):
    return min(x + np.random.randint(2, 10),
               10 * (x / 10 + 1),
               next_element)


def generate_form(title, num_elements=5, font_size="15pt", font='"Arial", sans-serif', randomfont=False):

    # Form will have a ratio between 15:12 and 12:15
    min_width, max_width = 12, 15
    ratio_wh = [np.random.randint(min_width, max_width), np.random.randint(min_width, max_width)]

    height_perc = np.random.randint(65, 80) # Percentage of the total browser height

    form = {
        "ratio": "{}:{}".format(*ratio_wh),
        "vspace": "{}".format(height_perc),
        "page_id": title,
        "border_thickness": "1",
        "font_family": font,
        "letter_spacing": "normal",
        "fontsize": str(font_size),
        "form_action": "/IntegriScreenServer/MainServer",
        "elements": [
            {
                "id": "form_title",
                "type": "title",
                "initialvalue": title.replace('_', ' '),
                "editable": "false",
                "ulc_x": "5",
                "ulc_y": "2",
                "width": "50",
                "height": "15"
            },
        ]
    }

    admissible_start_positions = list(set(range(10, 90, 2)))
    random_pick = np.random.choice(admissible_start_positions, num_elements, replace=False)
    elements_start_pos = sorted(random_pick)

    # Who takes the role of the button?
    button_elem = np.random.choice(range(num_elements))

    elements_coords = [(x, find_max_end(x, _i, elements_start_pos[_i+1] if _i+1 < len(elements_start_pos) else 90)) for _i, x in enumerate(elements_start_pos)]

    print elements_coords

    # Add other elements except the title
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

    # Convert all numbers to strings as well
    for elem in form['elements']:
        elem['ulc_x'] = str(elem['ulc_x'])
        elem['ulc_y'] = str(elem['ulc_y'])
        elem['width'] = str(elem['width'])
        elem['height'] = str(elem['height'])

    # Save the form to disk
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
