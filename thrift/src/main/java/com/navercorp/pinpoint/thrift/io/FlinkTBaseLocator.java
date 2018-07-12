/*
 * Copyright 2018 NAVER Corp.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.navercorp.pinpoint.thrift.io;

import com.navercorp.pinpoint.io.header.Header;
import com.navercorp.pinpoint.io.header.HeaderDataGenerator;
import com.navercorp.pinpoint.io.header.v1.HeaderV1;
import com.navercorp.pinpoint.io.header.v2.HeaderV2;
import com.navercorp.pinpoint.io.util.BodyFactory;
import com.navercorp.pinpoint.io.util.HeaderFactory;
import com.navercorp.pinpoint.io.util.TypeLocator;
import com.navercorp.pinpoint.io.util.TypeLocatorBuilder;
import com.navercorp.pinpoint.thrift.dto.flink.TFAgentStatBatch;
import org.apache.thrift.TBase;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Collections;

/**
 * @author minwoo.jung
 */
public class FlinkTBaseLocator {

    private Logger logger = LoggerFactory.getLogger(this.getClass());
    public static final short AGENT_STAT_BATCH = 1000;

    private final byte version;
    private final HeaderDataGenerator headerDataGenerator;
    private final TypeLocator<TBase<?, ?>> typeLocator;

    public FlinkTBaseLocator(byte version, HeaderDataGenerator headerDataGenerator) {
        if (version != HeaderV1.VERSION && version != HeaderV2.VERSION) {
            throw new IllegalArgumentException(String.format("could not select match header version. : 0x%02X", version));
        }
        this.version = version;

        if (headerDataGenerator == null) {
            throw new NullPointerException("headerDataGenerator must not be null.");
        }
        this.headerDataGenerator = headerDataGenerator;
        this.typeLocator = newTypeLocator();
    }

    private TypeLocator<TBase<?, ?>> newTypeLocator() {
        HeaderFactory headerFactory = new FlinkHeaderFactory();
        TypeLocatorBuilder<TBase<?, ?>> typeLocatorBuilder = new TypeLocatorBuilder<TBase<?, ?>>(headerFactory);
        typeLocatorBuilder.addBodyFactory(AGENT_STAT_BATCH, new BodyFactory<TBase<?, ?>>() {
            @Override
            public TBase<?, ?> getObject() {
                return new TFAgentStatBatch();
            }
        });

        TypeLocator<TBase<?, ?>> typeLocator = typeLocatorBuilder.build();

        if (version == HeaderV2.VERSION) {
            typeLocator = new FlinkTypeLocator(typeLocator);
        }

        return typeLocator;
    }

    public TypeLocator<TBase<?, ?>> getTypeLocator() {
        return typeLocator;
    }

    public class FlinkTypeLocator implements TypeLocator<TBase<?, ?>> {

        private final TypeLocator<TBase<?, ?>> delegate;

        public FlinkTypeLocator(TypeLocator<TBase<?, ?>> original) {
            if (original == null) {
                throw new NullPointerException("TypeLocator must not be null");
            }

            delegate = original;
        }

        @Override
        public TBase<?, ?> bodyLookup(short type) {
            return delegate.bodyLookup(type);
        }

        @Override
        public Header headerLookup(TBase<?, ?> body) {
            Header header = delegate.headerLookup(body);
            return new HeaderV2(header.getSignature(), header.getVersion(), header.getType(), headerDataGenerator.generate());
        }

        @Override
        public Header headerLookup(short type) {
            Header header = delegate.headerLookup(type);
            return new HeaderV2(header.getSignature(), header.getVersion(), header.getType(), headerDataGenerator.generate());
        }

        @Override
        public boolean isSupport(short type) {
            return delegate.isSupport(type);
        }

        @Override
        public boolean isSupport(Class<? extends TBase<?, ?>> clazz) {
            return delegate.isSupport(clazz);
        }
    }

    public class FlinkHeaderFactory implements HeaderFactory {
        @Override
        public Header newHeader(short type) {
            return createHeader(type);
        }
    };

    private Header createHeader(short type) {
        if (version == HeaderV1.VERSION) {
            return createHeaderV1(type);
        } else if (version == HeaderV2.VERSION) {
            return createHeaderV2(type);
        }

        throw new IllegalArgumentException("unsupported Header version : " + version);
    }

    private Header createHeaderV1(short type) {
        return new HeaderV1(type);
    }

    private Header createHeaderV2(short type) {
        return new HeaderV2(Header.SIGNATURE, HeaderV2.VERSION, type, Collections.EMPTY_MAP);
    }

}