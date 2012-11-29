package com.xpn.xwiki.objects.meta;

import javax.inject.Named;
import javax.inject.Singleton;

import org.xwiki.component.annotation.Component;

import com.xpn.xwiki.objects.classes.LatLonClass;
import com.xpn.xwiki.objects.classes.PropertyClassInterface;

@Component
@Named("LatLon")
@Singleton
public class LatLonMetaClass extends StringMetaClass
{
    /**
     * Constructor for EmailMetaClass.
     */
    public LatLonMetaClass()
    {
        setPrettyName("LatLon");
        setName(getClass().getAnnotation(Named.class).value());
    }

    @Override
    public PropertyClassInterface getInstance()
    {
        return new LatLonClass();
    }

}
