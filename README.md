# ANONERO
Privacy and security focused Monero wallet with advanced features in a slick UX.

### QUICKSTART
- Download the APK for the most current release [here](http://git.anonero5wmhraxqsvzq2ncgptq6gq45qoto6fnkfwughfl4gbt44swad.onion/ANONERO/ANONERO/releases) and install it

### DISCLAIMER
Be sure to back up your wallet recovery seed AND passphrase. 

ANON enforces passphras encryption on all seeds!

We are NOT responsible for lost or stolen funds.

### MAIN FEATURES
- Monero only
- Mandatory proxy
- No 3rd-party services
- Polyseed mnemonic
- Passphrase encryption
- No subaddress reuse
- Encrypted backups
- UTXO management
- Secure view-key syncing
- Airgapped transactions


### HOW TO BUILD

Monero is a pinned submodule, so cloning recursively gets the exact commit this release
builds against -- no manual linking, and no chance of building against the wrong tree.

1. Clone with submodules:
```
git -c http.proxy=socks5h://127.0.0.1:9050 clone --recurse-submodules \
    http://git.anonero5wmhraxqsvzq2ncgptq6gq45qoto6fnkfwughfl4gbt44swad.onion/ANONERO/ANONERO.git
```

2. Build the native libraries (`sudo` because it drives docker):
```
cd ANONERO/external-libs
sudo make android     # arm64-v8a + armeabi-v7a, for the APK
sudo make linux       # linux-x86_64, for the desktop build
sudo make             # all three
```

Builds run in a container, so the only host requirements are docker (or
`CONTAINER=podman`) and free disk. Parallelism defaults to `nproc`; override with
`make NPROC=4`.

If you cloned without `--recurse-submodules`:
```
git -c http.proxy=socks5h://127.0.0.1:9050 submodule update --init --recursive
```

Then, fire up Android Studio and build the APK.

### Donations
- Address: `8BQFYQTDMr9ibTsi3QMutG4EW3Gwv9a8N1XRLV95QBrg5THWSAt8no6GKgXErgEYzAUMiEoqZ6zHYUewj27bmvRD7JBCGmf`


