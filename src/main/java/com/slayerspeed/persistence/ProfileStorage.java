package com.slayerspeed.persistence;

/** Small account-profile boundary, also usable by deterministic persistence tests. */
public interface ProfileStorage
{
    String profileKey();
    String read(String key);
    void write(String key, String value);
    void remove(String key);
}
