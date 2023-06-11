import numpy as np

def identify_peek(t, N):
    t = np.array(t)
    N = np.array(N)
    number_of_steps = 0
    thresh = 13.6
    vals_in_mean = 5
    for i in range(len(N)):
        if i > vals_in_mean:
            mean = np.mean(N[i-vals_in_mean:i])
        else:
            mean = np.mean(N[:i])
        if mean > thresh:
            number_of_steps += 1
    return number_of_steps
