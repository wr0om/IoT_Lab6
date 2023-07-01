import numpy as np
import pandas as pd
import sklearn
import pickle
import tensorflow as tf
from tensorflow import keras
from tensorflow.keras.models import load_model


def calc_fft(file):
    fft_result_x = np.fft.fft(file['acc_x'])
    fft_result_y = np.fft.fft(file['acc_y'])
    fft_result_z = np.fft.fft(file['acc_z'])
    frequencies = np.fft.fftfreq(len(file['acc_x']))
    return fft_result_x, fft_result_y, fft_result_z, frequencies

def lable_to_letter(a):
    if a == 0:
        return 'SPACE'
    if a == 1:
        return 'DOT'
    else:
        return chr(a - 2 + ord('a'))


def identify_letter(t, x, y, z):
    data = {
        't': t,
        'acc_x': x,
        'acc_y': y,
        'acc_z': z
    }
    df = pd.DataFrame(data)
    fft_x, fft_y, fft_z, frequencies = calc_fft(df)


    model = load_model('/sdcard/csv_dir/model_2.h5')
    k = 22
    argument = np.concatenate((np.real(fft_x[:k]), np.imag(fft_x[:k]),
                                 np.real(fft_y[:k]), np.imag(fft_y[:k]),
                                    np.real(fft_z[:k]), np.imag(fft_z[:k])), axis=None)
    argument = np.reshape(argument, (1, argument.shape[0], 1))
    P = model.predict(argument)
    return lable_to_letter(np.argmax(P))

    # svm_model = pickle.load(open('/sdcard/csv_dir/svm_model.sav', 'rb'))
    # k = 22
    # argument = np.concatenate((np.real(fft_x[:k]), np.imag(fft_x[:k]),
    #                            np.real(fft_y[:k]), np.imag(fft_y[:k]),
    #                            np.real(fft_z[:k]), np.imag(fft_z[:k])), axis=None).reshape(1, -1)
    # return lable_to_letter(svm_model.predict(argument)[0])






