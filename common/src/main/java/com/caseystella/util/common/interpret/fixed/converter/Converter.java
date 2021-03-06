package com.caseystella.util.common.interpret.fixed.converter;


import com.caseystella.util.common.interpret.fixed.Field;

import java.nio.ByteBuffer;
import java.util.Map;

/**
 * Created by cstella on 9/3/14.
 */
public enum Converter implements IConverter
{
    TEXT(new TextConverter())
    ,BINARY(new BinaryConverter())
    ;
    IConverter converter;
    Converter(IConverter converter)
    {
        this.converter = converter;
    }

    public Object convert(ByteBuffer bytes, Field.Type type, Map<String, Object> config)
    {
       return converter.convert(bytes, type, config);
    }

    public void validate(Map<String, Object> config)
    {
        converter.validate(config);
    }

}
