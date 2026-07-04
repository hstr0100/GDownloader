/*
 * Copyright (C) 2026 hstr0100
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
package net.brlns.gdownloader.persistence.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Embeddable;
import lombok.Data;

/**
 * @author Gabriel / hstr0100 / vertx010
 */
@Data
@Embeddable
public class FormatInfoEmbeddable {

    @Column(name = "format_id", length = 128)
    private String formatId;

    @Column(name = "ext", length = 32)
    private String ext;

    @Column(name = "resolution", length = 48)
    private String resolution;

    @Column(name = "format_note", length = 256)
    private String formatNote;

    @Column(name = "vcodec", length = 128)
    private String vcodec;

    @Column(name = "acodec", length = 128)
    private String acodec;

    @Column(name = "fps")
    private Double fps;

    @Column(name = "tbr")
    private Double tbr;

    @Column(name = "vbr")
    private Double vbr;

    @Column(name = "abr")
    private Double abr;

    @Column(name = "filesize")
    private Long filesize;

    @Column(name = "filesize_approx")
    private Long filesizeApprox;

    @Column(name = "format", length = 512)
    private String format;
}
