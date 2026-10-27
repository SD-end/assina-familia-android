package com.example.assinafamilia.data.local;

import androidx.room.TypeConverter;

import java.util.Date;

public class Converters {

    @TypeConverter
    public static Date fromTimestamp(Long valor) {
        return valor == null ? null : new Date(valor);
    }

    @TypeConverter
    public static Long dateToTimestamp(Date data) {
        return data == null ? null : data.getTime();
    }
}
