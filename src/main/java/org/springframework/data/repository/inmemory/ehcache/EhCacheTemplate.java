/*
 * Copyright 2014 the original author or authors.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.springframework.data.repository.inmemory.ehcache;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

import net.sf.ehcache.search.Attribute;
import net.sf.ehcache.search.Direction;
import net.sf.ehcache.search.Query;
import net.sf.ehcache.search.Result;
import net.sf.ehcache.search.Results;

import org.springframework.core.convert.converter.Converter;
import org.springframework.data.domain.Sort;
import org.springframework.data.mapping.PersistentEntity;
import org.springframework.data.mapping.PersistentProperty;
import org.springframework.data.mapping.context.MappingContext;
import org.springframework.data.repository.inmemory.AbstractInMemoryOperations;
import org.springframework.data.util.ListConverter;

/**
 * @author Christoph Strobl
 */
public class EhCacheTemplate extends AbstractInMemoryOperations<EhCacheQuery> {

	private EhCacheAdapter adapter;

	public EhCacheTemplate() {
		this(new EhCacheAdapter());
	}

	public EhCacheTemplate(EhCacheAdapter adapter) {
		super();
		this.adapter = adapter;
	}

	public EhCacheTemplate(
			MappingContext<? extends PersistentEntity<?, ? extends PersistentProperty>, ? extends PersistentProperty<?>> mappingContext) {
		this(new EhCacheAdapter(), mappingContext);
	}

	public EhCacheTemplate(
			EhCacheAdapter adapter,
			MappingContext<? extends PersistentEntity<?, ? extends PersistentProperty>, ? extends PersistentProperty<?>> mappingContext) {
		super(mappingContext);
		this.adapter = adapter;
	}

	@Override
	public <T> List<T> read(final int offset, final int rows, final Class<T> type) {

		List<T> tmp = read(type);

		if (offset > tmp.size()) {
			return Collections.emptyList();
		}
		int rowsToUse = (offset + rows > tmp.size()) ? tmp.size() - offset : rows;
		return new ArrayList<T>(tmp.subList(offset, offset + rowsToUse));

	}

	@Override
	public <T> List<T> read(Sort sort, Class<T> type) {
		return read(new EhCacheQuery(null).orderBy(sort), type);
	}

	@Override
	public <T> List<T> read(int offset, int rows, Sort sort, Class<T> type) {
		return read(new EhCacheQuery(null).skip(offset).limit(rows).orderBy(sort), type);
	}

	@Override
	protected EhCacheAdapter getAdapter() {
		return this.adapter;
	}

	@Override
	protected <T> List<T> doRead(EhCacheQuery query, Class<T> type) {

		Query q = prepareQuery(query, type);

		Results result = q.execute();

		ResultConverter<T> conv = new ResultConverter<T>();
		ListConverter<Result, T> listConc = new ListConverter<Result, T>(conv);

		if (query.getRows() > 0 && query.getOffset() >= 0) {
			return listConc.convert(result.range(query.getOffset(), query.getRows()));
		}
		return listConc.convert(result.all());
	}

	private class ResultConverter<T> implements Converter<Result, T> {

		@SuppressWarnings("unchecked")
		@Override
		public T convert(Result source) {
			return (T) source.getValue();
		}
	}

	@Override
	protected long doCount(EhCacheQuery query, Class<?> type) {

		Query q = prepareQuery(query, type);
		return q.execute().size();
	}

	@Override
	public void destroy() throws Exception {
		getAdapter().clear();
	}

	private <T> Query prepareQuery(EhCacheQuery query, Class<T> type) {

		Query cacheQuery = getAdapter().getCache(resolveTypeAlias(type)).createQuery().includeValues();

		if (query.getCritieria() != null) {
			cacheQuery.addCriteria(query.getCritieria());
		}

		if (query.getSort() != null) {

			for (Sort.Order order : query.getSort()) {
				cacheQuery.addOrderBy(new Attribute(order.getProperty()), org.springframework.data.domain.Sort.Direction.ASC
						.equals(order.getDirection()) ? Direction.ASCENDING : Direction.DESCENDING);
			}
		}

		cacheQuery.end();
		return cacheQuery;
	}

}