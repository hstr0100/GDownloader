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
package net.brlns.gdownloader.util;

import java.util.ArrayDeque;
import java.util.Iterator;
import java.util.concurrent.locks.ReentrantReadWriteLock;

/**
 * @author Gabriel / hstr0100 / vertx010
 */
public class ConcurrentRearrangeableDeque<T> extends ArrayDeque<T>{

    private final ReentrantReadWriteLock lock = new ReentrantReadWriteLock();

    public void moveToPosition(T item, int newPosition){
        lock.writeLock().lock();

        try{
            if(!this.contains(item)){
                throw new IllegalArgumentException("Item not found in the deque.");
            }

            this.remove(item);

            if(newPosition < 0 || newPosition > this.size()){
                throw new IndexOutOfBoundsException("New position out of bounds.");
            }

            Iterator<T> it = this.iterator();
            ArrayDeque<T> tempDeque = new ArrayDeque<>();

            int index = 0;
            while(it.hasNext()){
                if(index == newPosition){
                    tempDeque.add(item);
                }

                tempDeque.add(it.next());
                index++;
            }

            if(newPosition == this.size()){
                tempDeque.add(item);
            }

            this.clear();
            this.addAll(tempDeque);
        }finally{
            lock.writeLock().unlock();
        }
    }

    public void swap(T item1, T item2){
        lock.writeLock().lock();

        try{
            if(!this.contains(item1) || !this.contains(item2)){
                throw new IllegalArgumentException("One or both items not found in the deque.");
            }

            ArrayDeque<T> tempDeque = new ArrayDeque<>();

            for(T item : this){
                if(item.equals(item1)){
                    tempDeque.add(item2);
                }else if(item.equals(item2)){
                    tempDeque.add(item1);
                }else{
                    tempDeque.add(item);
                }
            }

            this.clear();
            this.addAll(tempDeque);
        }finally{
            lock.writeLock().unlock();
        }
    }

    @Override
    public boolean offer(T item){
        lock.writeLock().lock();

        try{
            return super.offer(item);
        }finally{
            lock.writeLock().unlock();
        }
    }

    @Override
    public boolean offerLast(T item){
        lock.writeLock().lock();

        try{
            return super.offerLast(item);
        }finally{
            lock.writeLock().unlock();
        }
    }

    @Override
    public T peek(){
        lock.readLock().lock();

        try{
            return super.peek();
        }finally{
            lock.readLock().unlock();
        }
    }

    @Override
    public T poll(){
        lock.writeLock().lock();

        try{
            return super.poll();
        }finally{
            lock.writeLock().unlock();
        }
    }

    @Override
    public boolean remove(Object o){
        lock.writeLock().lock();

        try{
            return super.remove(o);
        }finally{
            lock.writeLock().unlock();
        }
    }

    @Override
    public int size(){
        lock.readLock().lock();

        try{
            return super.size();
        }finally{
            lock.readLock().unlock();
        }
    }

    @Override
    public boolean isEmpty(){
        lock.readLock().lock();

        try{
            return super.isEmpty();
        }finally{
            lock.readLock().unlock();
        }
    }

    @Override
    public boolean contains(Object obj){
        lock.readLock().lock();

        try{
            return super.contains(obj);
        }finally{
            lock.readLock().unlock();
        }
    }

    // TODO UnsupportedOperationException for the rest of the overridable methods
}
