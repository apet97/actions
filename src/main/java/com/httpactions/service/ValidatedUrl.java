package com.httpactions.service;

import java.net.InetAddress;
import java.net.URI;

public record ValidatedUrl(URI uri, String host, InetAddress resolvedAddress) {
}
