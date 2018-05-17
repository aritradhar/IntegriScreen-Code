import argparse
import time

from selenium import webdriver


def spawn_tester(source, atk_mode, atk_type):
    atk_string = "atk_mode={}&atk_type={}".format(atk_mode, atk_type) if atk_mode and atk_type else ''
    driver.get("http://tildem.inf.ethz.ch/generated/tester.html?setup={}&{}".format(source, atk_string))


def test_page():
    inputs = driver.find_elements_by_css_selector("input[type='text'], textarea")
    for _input in inputs:
        length = len(_input.text)
        _input.clear()
        target_word = "whatever"  # TODO draw words
        for l1, l2 in zip(target_word, target_word[1:] + '$'):
            _input.send_keys(l1)
            if l2 != '$':
                time.sleep(0.18)  # TODO draw a value according to distance l1-l2
    driver.find_elements_by_css_selector("input[type='submit']")[0].click()
    print driver.get_log('browser')


def next_page():
    driver.execute_script('next()')


if __name__ == '__main__':
    parser = argparse.ArgumentParser()
    parser.add_argument('source')
    parser.add_argument('--attacker', choices=['parallel', 'inactive', ])
    parser.add_argument('--attack_type', choices=['replace_char', 'flip_chars', 'add_char', 'remove_char'])
    parser.add_argument('--time', type=float, default=5)

    args = parser.parse_args()

    driver = webdriver.Chrome()
    spawn_tester(args.source, args.attacker, args.attack_type)

    while True:
        driver.switch_to.frame(driver.find_element_by_id('target'))
        test_page()
        driver.switch_to.default_content()
        time.sleep(args.time)
        next_page()
