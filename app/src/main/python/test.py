import numpy as np

def identify_peek(t, N):
    t = np.array(t)
    N = np.array(N)
    number_of_steps = 0
    thresh = 20
    for i in range(len(N)):
        if N[i] > thresh:
            number_of_steps += 1
    return number_of_steps
