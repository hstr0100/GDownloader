/*
 * Copyright (C) 2024 hstr0100
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program.  If not, see <http://www.gnu.org/licenses/>.
 */
module net.brlns.gdownloader {

    requires transitive static lombok;

    requires transitive java.datatransfer;
    requires transitive java.desktop;
    requires java.base;
    requires java.net.http;
    requires jdk.crypto.ec;
    requires java.logging;
    requires java.management;
    requires java.naming;

    requires transitive com.github.kwhat.jnativehook;
    requires transitive com.fasterxml.jackson.databind;
    requires transitive jakarta.annotation;
    requires transitive jakarta.persistence;
    requires com.fasterxml.jackson.annotation;
    requires com.fasterxml.jackson.core;
    requires eclipselink;
    requires org.hsqldb;
    requires org.jsoup;
    requires org.slf4j;
    requires ch.qos.logback.classic;
    requires ch.qos.logback.core;
    requires com.sun.jna;
    requires com.sun.jna.platform;

    exports net.brlns.gdownloader;
    exports net.brlns.gdownloader.clipboard;
    exports net.brlns.gdownloader.downloader;
    exports net.brlns.gdownloader.downloader.enums;
    exports net.brlns.gdownloader.downloader.structs;
    exports net.brlns.gdownloader.event;
    exports net.brlns.gdownloader.event.impl;
    exports net.brlns.gdownloader.persistence;
    exports net.brlns.gdownloader.persistence.converter;
    exports net.brlns.gdownloader.persistence.entity;
    exports net.brlns.gdownloader.persistence.repository;
    exports net.brlns.gdownloader.server;
    exports net.brlns.gdownloader.server.result;
    exports net.brlns.gdownloader.settings;
    exports net.brlns.gdownloader.settings.enums;
    exports net.brlns.gdownloader.settings.filters;
    exports net.brlns.gdownloader.ui;
    exports net.brlns.gdownloader.ui.custom;
    exports net.brlns.gdownloader.ui.dnd;
    exports net.brlns.gdownloader.ui.menu;
    exports net.brlns.gdownloader.ui.themes;
    exports net.brlns.gdownloader.updater;
    exports net.brlns.gdownloader.util;
    exports net.brlns.gdownloader.util.collection;

    uses javax.imageio.spi.ImageInputStreamSpi;
    uses javax.imageio.spi.ImageOutputStreamSpi;
    uses javax.imageio.spi.ImageWriterSpi;
    uses javax.imageio.spi.ImageReaderSpi;

    opens net.brlns.gdownloader.persistence.converter;
    opens net.brlns.gdownloader.persistence.entity;
    opens net.brlns.gdownloader.server.result;
}
