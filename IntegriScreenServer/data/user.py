import argparse
import numpy as np
import time

from selenium import webdriver
from selenium.webdriver import DesiredCapabilities

WORDS = np.loadtxt('rnd_forms/dictionary', dtype=str)
WORDS_LENGTHS = {_i: [y for y in WORDS if len(y) == _i] for _i in np.unique([len(x) for x in WORDS])}

KBD = np.array([
    list("qwertyuiop"),
    list("asdfghjkl;"),
    list("zxcvbnm,./"),
])


def spawn_tester(source, atk_mode, atk_type):
    atk_string = "atk_mode={}&atk_type={}".format(atk_mode, atk_type) if atk_mode and atk_type else ''
    driver.get("http://tildem.inf.ethz.ch/generated/tester.html?setup={}&{}".format(source, atk_string))
    time.sleep(3)  # HACK: waits optimistically for the page to load


def test_page():
    inputs = driver.find_elements_by_css_selector("input[type='text'], textarea")
    for _input in inputs:
        word = _input.text or _input.get_attribute('value')
        target_word = np.random.choice(WORDS_LENGTHS[len(word)])
        _input.clear()
        for l1, l2 in zip(target_word, target_word[1:] + '$'):
            _input.send_keys(l1)
            if l2 != '$':
                ch_idx1, ch_idx2 = np.nonzero(KBD == l1), np.nonzero(KBD == l2)  # position of letters in a keyboard
                distance = abs(ch_idx1[0] - ch_idx2[0]) + abs(ch_idx1[1] - ch_idx2[1])  # result is a list
                # distance between two keys is in [1, 11]
                # typing speed should be between 100-200 ms per keypair
                time.sleep(np.random.normal(120 + 60 / 11.0 * distance, 10) / 1000.0)
    driver.find_elements_by_css_selector("input[type='submit']")[0].click()
    for log in driver.get_log('browser'):
        print log['message']


def next_page():
    driver.execute_script('next()')


if __name__ == '__main__':
    parser = argparse.ArgumentParser()
    parser.add_argument('source')
    parser.add_argument('--attacker', choices=['parallel', 'inactive'])
    parser.add_argument('--attack_type', choices=['replace_char', 'flip_chars', 'add_char', 'remove_char'])
    parser.add_argument('--time', type=float, default=3)

    args = parser.parse_args()

    d = DesiredCapabilities.CHROME
    d['loggingPrefs'] = {'browser': 'ALL'}
    driver = webdriver.Chrome(desired_capabilities=d)
    spawn_tester(args.source, args.attacker, args.attack_type)

    while True:
        driver.switch_to.frame(driver.find_element_by_id('target'))
        test_page()
        driver.switch_to.default_content()
        time.sleep(args.time)
        next_page()
